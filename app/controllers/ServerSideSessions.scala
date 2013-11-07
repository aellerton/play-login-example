package controllers

import models.User
import java.util.UUID
import state.{JsonCache => Cache}

class NoSessionError(message: String = "No Session") extends Exception(message)

class ExpiredSessionError(message: String = "Expired Session") extends Exception(message)

case class ServerSideSessionState(user: User)

object ServerSideSessions {

  def get(token: String): Option[ServerSideSessionState] = {
    val result: Option[ServerSideSessionState] = Cache.get[ServerSideSessionState](token)
    //println(s"XXX get server session $token = $result")

//      .map { retrieved => // => _.asInstanceOf[String]).map { json =>
//    // Surely there is a cleaner way than this with Play:
//      JsonMapper.fromJson[ServerSideSessionState](retrieved.asInstanceOf[String]), classOf[ServerSideSessionState])
//    }
    result
  }

  def delete(token: String) {
    Cache.remove(token)
  }

  /**
   * Generate a new unique session key.
   *
   * @return a new session key
   */
  def newToken: String = UUID.randomUUID().toString

  /**
   * Create a new session and save it.
   *
   * @return the key of the new session.
   */
  def create(state: ServerSideSessionState, expirySec: Option[Int]): String = {
    val token = newToken
    update(token, state, expirySec)
    token
  }

  def update(token: String, state: ServerSideSessionState, expirySec: Option[Int]): ServerSideSessionState = {
    //println(s"XXX update token=$token state=$state json=${JsonMapper.toJson(state).toString}")
    Cache.set(token, state, expirySec)
    //println("IMMEDIATE GET="+Cache.get[ServerSideSessionState](token))
    state
  }
}
