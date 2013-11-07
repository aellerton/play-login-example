package controllers

import scala.concurrent.Future
import play.api.mvc._
import play.api.data._
import play.api.libs.json.Json
import actions.{LoginData, AuthenticatedRequest, AuthenticatedRequests}


object AjaxLogin extends Controller with AuthenticatedRequests {

  override def authenticationRequired[A](request: Request[A]): Future[SimpleResult] = {
    Future.successful(
      Redirect(routes.AjaxLogin.showAjaxLoginForm).withSession("goto" -> request.path)
      //.flashing(("error", message.getOrElse("Auth failed for unknown reason."))
    )
  }

  override def authenticationFormFailure[A](loginFormWithErrors: Form[LoginData])(request: Request[A]): Future[SimpleResult] = {
    Future.successful(BadRequest(loginFormWithErrors.errorsAsJson))
  }

  override def authenticationFailure[A](loginData: LoginData)(request: Request[A]): Future[SimpleResult] = {
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
    val goto: String = request.session.get("goto").getOrElse("")

    Ok(Json.obj(
      "goto" -> goto
    )).withSession(
      serverSideSessionTokenKeyName -> request.token
    )
  }

  def signOut = AbandonAuthentication { implicit request =>
    //Redirect(routes.AjaxLogin.showAjaxLoginForm).withNewSession.flashing("success" -> "You've been logged out")
    Ok("You're logged out.").withNewSession
  }

  def authenticatedIndex = RequireAuthentication { implicit request: AuthenticatedRequest[AnyContent] =>
    Ok(views.html.authorised(s"You're in!."))
  }

}
