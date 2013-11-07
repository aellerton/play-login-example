package state

import scala.Some
import scala.reflect.ClassTag


/**
 * Hours of frustration with the default play ehcache or the redis plugin
 * cache have led me to build my own.
 *
 * In the case of the redis cache it always serializes so when you look at
 * data in a client you'll see results like:
 *
 * {{{
 *   redis 127.0.0.1:6379> get 10e2d2f8-0850-4407-a05e-48a6897db706
 *   "oos-rO0ABXQAAnt9"   <-- not so useful
 * }}}
 *
 * As I know I want to read and write JSON, a cache that does exactly that is handy.
 */
trait JsonCache {
  def set(key: String, any: AnyRef, ttl: Int): Unit = set(key, any, Some(ttl))
  def set(key: String, any: AnyRef, ttl: Option[Int]=None): Unit
  def get[T](key: String)(implicit tag : reflect.ClassTag[T] ): Option[T]
  def remove(key: String): Unit
}

trait RedisJsonCache extends JsonCache {
  //  import play.api.Play.current
  //  import com.typesafe.plugin.RedisPlugin
  //  lazy val pool = use[RedisPlugin].sedisPool

  import org.sedis._
  import redis.clients.jedis._
  import Dress._

  // TODO: from configuration (use import play.Configuration or import play.api.Play.current?)
  lazy val pool = new Pool(new JedisPool(new JedisPoolConfig(), "localhost", 6379, 2000))

  def set(key: String, any: AnyRef, ttl: Option[Int]): Unit = {
    pool.withClient { client =>
      client.set(key, JsonMapper.toJson(any))
      ttl.map { ms => client.expire(key, ms) }
    }
  }

  def get[T](key: String)(implicit tag : ClassTag[T] ): Option[T] = {
    pool.withClient { client =>
      client.get(key) match {
        case Some(json: String) => {

//          Logger.warn(s"retrieve cache [$key] -> $json")
//          println(s"XXX retrieve cache [$key] -> $json")
//          println(s"XXX get() tag ="+tag)

          JsonMapper.fromJson[T](json)(tag)
        }
        case None => None
      }
    }
  }

  def remove(key: String): Unit = {
    pool.withJedisClient { client =>
      client.del(key)
    }
  }
}

object JsonCache extends RedisJsonCache