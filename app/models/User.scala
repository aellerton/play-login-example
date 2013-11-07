package models

/**
 * Something resembling a user. Real classes would add all sorts of other data.
 * @param name the login username for this user.
 */
case class User(name: String)

/**
 * Define the API for a service that can authenticate and return a User object.
 */
trait UserService {
  def authenticate(name: String, password: String): Option[User]
}

/**
 * A pretend service that can authenticate and return User objects.
 * Clearly a real implementation will make WS calls or, if you're
 * so inclined, database calls directly.
 */
object MockUserService extends UserService {

  // simple hard-coded map of users to passwords
  val users = Map(
    "bob" -> "foo",
    "sally" -> "bar"
  )

  def checkPassword(name: String, password: String): Boolean = {
    users.get(name).filter { _ == password }.isDefined
  }

  def authenticate(name: String, password: String): Option[User] = {
    if (checkPassword(name, password)) Some(User(name))
    else None
  }
}
