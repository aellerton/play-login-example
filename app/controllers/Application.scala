package controllers

import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._ // for "Cannot find an implicit ExecutionContext..."

case class User(name: String)

case class LoginData(name: String, password: String)

case class AuthenticatedRequest[A](user: User, request: Request[A]) extends WrappedRequest(request)

object MockUserService {

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

trait Authenticator {
  self: Controller =>

  val loginDataForm = Form(
    mapping(
      "name" -> text,
      "password" -> text
    )(LoginData.apply)(LoginData.unapply)
  )

  /**
   * An Action that automatically binds a form with User, adds the parsed value to the
   * request if successful, or returning BadRequest otherwise
   */
  /*object AuthenticateUserByFormAction1 extends ActionBuilder[AuthenticatedRequest] {
    def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {

      // Authenticate the action and wrap the request in an authenticated request
      getUserFromRequest(request).flatMap { user =>
        block(new AuthenticatedRequest(user, request))
      }
    }
  }*/

  def unauthorized(failedLoginData: LoginData): Future[SimpleResult] = {
    Future.successful(Unauthorized)
  }

  object AuthenticateUserByFormAction extends ActionBuilder[AuthenticatedRequest] {
    def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {

      loginDataForm.bindFromRequest()(request).fold(

        // when the form is not valid:
        formWithErrors => Future.successful(BadRequest(formWithErrors.errorsAsJson)),

        // when the form is valid, authenticate and invoke the next processing block
        // (unless the authentication fails, in which case return a 401 status):
        loginData => MockUserService.authenticate(loginData.name, loginData.password).map { user =>
          block(new AuthenticatedRequest(user, request))
        } getOrElse {
          unauthorized(loginData)
        }
      )
    }
  }

}


object Application extends Controller with Authenticator {

  def index = Action {
    Ok(views.html.index("Any old message"))
  }

  def showPlainLoginForm = Action {
    Ok(views.html.login(loginDataForm))
  }

  def authenticatePlainLoginForm = AuthenticateUserByFormAction { implicit request =>
    Ok(views.html.authorised("By using a plain old POST form, it looks like you are: "+request.user))
  }

//  def authenticateFormWithSession = AuthenticateUserByFormAction { implicit request =>
//    request.session
//    Ok("TODO")
//  }
//
//  def authenticateAjaxWithSession = AuthenticateUserByFormAction { implicit request =>
//    Ok("TODO")
//  }
//
//  def authenticateJson = Action {
//    Ok("TODO")
//  }
//
//  def authorised = WithUserSession { request =>
//    Ok(views.html.index("TODO: this isn't yet done"))
//  }

  // Override the basic 401 response that the Authenticator trait returns for failed auth.
  override def unauthorized(failedLoginData: LoginData): Future[SimpleResult] = {
    Future.successful(Unauthorized(views.html.failedLogin()))
  }


}


/*

  /**
   * Update the department for the given id.
   * Uses the `DepartmentFromForm` Action to automatically bind to the Department form.
   * Test with: curl -X PUT http://localhost:9000/ab/departments/1 -d "name=foo"
   */
  def updateDepartment(id: Long) = DepartmentFromForm(departmentForm) { request =>
    // ~ Department.update(department)
    Ok(s"Received ${request.department.name}\n")
  }

  def updateDepartmentAsync(id: Long) = DepartmentFromForm(departmentForm).async { request =>
    // ~ Department.update(department)
    resolve(Ok(s"Received ${request.department.name}\n"))
  }

}

*/
