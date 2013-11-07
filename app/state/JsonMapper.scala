package state

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import scala.reflect.ClassTag

/**
 * I think it's pretty silly to do this, but I can't see an API
 * in the default play.libs.Json that is as convenient, or in the
 * case of taking a string and producing a typed Scala object, *actually works*.
 */
object JsonMapper {
  lazy val mapper = {
    val m = new ObjectMapper()
    m.registerModule(DefaultScalaModule)
    m
  }

  def toJson(any: AnyRef): String = mapper.writeValueAsString(any)

  /** Given a string of Json, return a typed Scala object.
    *
    * Note that it seems critical to call this with the type explicitly stated, so:
    * {{{
    *   // bad
    *   val foo = fromJson(someString)
    *
    *   // good
    *   val bar = fromJson[Bar](someString)
    *
    * }}}
    *
    * @param src json string
    * @param tag result type, hopefully implicit but you can always be explicit if you want.
    * @tparam T any scala type (I think)
    * @return option of the result.
    */
  def fromJson[T](src: String)(implicit tag : ClassTag[T] ): Option[T] = {
    src match {
      case null => None
      case "" => None
      case nonBlank: String => Some[T](mapper.readValue(nonBlank, tag.runtimeClass).asInstanceOf[T])
    }
  }

}
