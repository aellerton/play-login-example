package controllers

import scala.concurrent.Future
import play.api.mvc._
import play.api.data._
import actions.{LoginData, AuthenticatedRequest, AuthenticatedRequests}


object PlainLogin extends Controller with AuthenticatedRequests {

  override def authenticationRequired[A](request: Request[A]): Future[SimpleResult] = {
    Future.successful(
      Redirect(routes.PlainLogin.showPlainLoginForm).withSession("goto" -> request.path)
      //.flashing(("error", message.getOrElse("Auth failed for unknown reason."))
    )
  }

  def authenticationFormFailure[A](loginFormWithErrors: Form[LoginData])(request: Request[A]): Future[SimpleResult] = {
    Future.successful(BadRequest(views.html.plainLogin
      (loginFormWithErrors)
      (request.asInstanceOf[Request[play.api.mvc.AnyContent]] // bit ugly ... any better way?
    )))
  }

  override def authenticationFailure[A](loginData: LoginData)(request: Request[A]): Future[SimpleResult] = {
    Future.successful(Ok(views.html.plainLogin(
      loginDataForm.fill(loginData).withGlobalError("Invalid user/password"))
      (request.asInstanceOf[Request[play.api.mvc.AnyContent]]) // bit ugly ... any better way?
    ))
  }


  def showPlainLoginForm = Action { implicit request =>
    Ok(views.html.plainLogin(loginDataForm))
  }

  def authenticatePlainLoginForm = PerformAuthentication { implicit request =>
    Ok(views.html.authorised("By using a plain old POST form, it looks like you are: "+request.user))
  }

}
