package controllers

import scala.concurrent.Future
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import actions.{LoginData, AuthenticatedRequest, AuthenticatedRequests}
import models.MockUserService
import play.api.libs.json.Json


trait AuthenticationActions {
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
          block(new AuthenticatedRequest(user, "TODO", request))
        } getOrElse {
          unauthorized(loginData)
        }
      )
    }
  }
}

trait AuthenticatedSessionActions {
  def unauthorized[A](message: Option[String] = None)(implicit request: Request[A]): Future[SimpleResult] = {
    ???
  }




  /**
   * The AuthenticatedUserAction validates that a session cookie is in place.
   * If it is, it retrieves it and loads that user into the request.
   * If it isn't, it redirects the user to login.
   */
  object AuthenticatedUserAction extends ActionBuilder[AuthenticatedRequest] {

    def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {
      getSession(request) match {
        case Right(request: AuthenticatedRequest[A]) => block(request)
        case Left(e: NoSessionError)      => unauthorized(Some("No session in place"))(request)
        case Left(e: ExpiredSessionError) => unauthorized(Some("Session expired"))(request)
        case Left(other: Throwable)       => throw other
      }
    }

    def getSession[A](request: Request[A]): Either[Exception, AuthenticatedRequest[A]] = {
      for {
        token <- request.session.get("token").toRight(new NoSessionError).right
        session <- ServerSideSessions.get(token).toRight(new ExpiredSessionError).right
      } yield {
        AuthenticatedRequest[A](session.user, token, request)
      }
    }
  }
}



object Application extends Controller { //with AuthenticationActions with AuthenticatedSessionActions {

  val logger = play.Logger.of(getClass)

  def index = Action {
    Ok(views.html.index("This is a plain old index page."))
  }
}


object PlainLogin extends Controller with AuthenticationActions {
  def showPlainLoginForm = Action { implicit request =>
    Ok(views.html.plainLogin(loginDataForm))
  }

  def authenticatePlainLoginForm = AuthenticateUserByFormAction { implicit request =>
    Ok(views.html.authorised("By using a plain old POST form, it looks like you are: "+request.user))
  }

  // Override the basic 401 response that the Authenticator trait returns for failed auth.
  override def unauthorized(failedLoginData: LoginData): Future[SimpleResult] = {
    Future.successful(Unauthorized(views.html.failedLogin()))
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
}

/*
trait AuthenticationHelper extends ActionBuilder[AuthenticatedRequest] {

  def unauthorized(message: String): Future[SimpleResult]

  def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {
    getSession(request) match {
      case Right(request: AuthenticatedRequest[A]) => block(request)
      case Left(e: NoSessionError)      => unauthorized("No session in place")//(request)
      case Left(e: ExpiredSessionError) => unauthorized("Session expired")//(request)
      case Left(other: Throwable)       => throw other
    }
  }

  def getSession[A](request: Request[A]): Either[Exception, AuthenticatedRequest[A]] = {
    for {
      token <- request.session.get("token").toRight(new NoSessionError).right
      session <- Sessions.get(token).toRight(new ExpiredSessionError).right
    } yield {
      AuthenticatedRequest[A](session.user, request)
    }
  }
}
*/



object AjaxLogin extends Controller with AuthenticatedRequests { //AuthenticationActions with AuthenticatedSessionActions {

  def authenticationRequired[A](request: Request[A]): Future[SimpleResult] = {
    Future.successful(
      Redirect(routes.AjaxLogin.showAjaxLoginForm).withSession("goto" -> request.path)
      //.flashing(("error", message.getOrElse("Auth failed for unknown reason."))
    )
  }

  def authenticationFormFailure[A](loginFormWithErrors: Form[LoginData])(request: Request[A]): Future[SimpleResult] = {
    Future.successful(BadRequest(loginFormWithErrors.errorsAsJson))
  }

  def authenticationFailure[A](loginData: LoginData)(request: Request[A]): Future[SimpleResult] = {
    Future.successful(Unauthorized("Invalid user/password"))
  }

  /**
   * Just shows a login form that works by AJAX calls.
   */
  def showAjaxLoginForm = Action { implicit request =>
    Ok(views.html.ajaxLogin())
  }

  /**
   * Login data is sent by AJAX in JSON format.
   * This method authenticates the user and, if successful, puts a new session
   * in place so subsequent authenticated pages don't require the user to log in again.
   *
   * @return empty page with 200 (login ok) or 401 (unauthorised)
   */
  def authenticateAjaxJson = PerformAuthentication { implicit request =>
    println("XXX: should redirect to"+request.session.get("goto"))
//    println(s"XXX: new token [${request.token}] and user [${request.user}]")
    val goto: String = request.session.get("goto").getOrElse("")
    Ok(Json.obj(//"status" ->"OK"
      "goto" -> goto
//      //"user" -> request.user.name
    )).withSession(
      serverSideSessionTokenKeyName -> request.token
      //"goto" -> request.session.get("goto").getOrElse("")
    )
  }

  def signOut = AbandonAuthentication { implicit request =>
    //Redirect(routes.AjaxLogin.showAjaxLoginForm).withNewSession.flashing("success" -> "You've been logged out")
    Ok("You're logged out.").withNewSession
  }

  def authenticatedIndex = RequireAuthentication { implicit request: AuthenticatedRequest[AnyContent] =>
    Ok(views.html.authorised(s"From the AjaxLogin controller."))
  }

  /*
  def authenticateAjaxForm = AuthenticateUserByFormAction { implicit request =>
    Ok("Authenticated! "+request.user)
  }

  /**
   * The AuthenticatedUserAction validates that a session cookie is in place.
   * If it is, it retrieves it and loads that user into the request.
   * If it isn't, it redirects the user to login.
   */
  def authorisedBySession = AuthenticatedUserAction { implicit request: AuthenticatedRequest[AnyContent] =>
    Ok(views.html.authorised(s"To get here, you must have a session in place, congratulations: $request.user!"))
  }

  // Override the basic 401 response that the Authenticator trait returns for failed auth.
  override def unauthorized(failedLoginData: LoginData): Future[SimpleResult] = {
    Future.successful(Unauthorized(failedLoginData.()))
  }

  override def unauthorized[A](message: Option[String] = None)(implicit request: Request[A]): Future[SimpleResult] = {
    Future.successful(
      Redirect(routes.Application.showAjaxLoginForm)
        .flashing(("error", message.getOrElse("Auth failed for unknown reason."))
      )
    )
  }
  */


}
