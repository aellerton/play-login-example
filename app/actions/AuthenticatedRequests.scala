package actions

import play.api.mvc.{WrappedRequest, ActionBuilder, SimpleResult, Request}
import scala.concurrent.Future
import controllers.{ServerSideSessionState, ExpiredSessionError, ServerSideSessions, NoSessionError}

import play.api.libs.concurrent.Execution.Implicits._
import play.cache.Cache
import play.libs.Json
import models.MockUserService
import play.api.data.Form
import play.api.data.Forms._
import models.User
import play.api.mvc.SimpleResult


case class LoginData(name: String, password: String)

/**
 * Represents an extension of Request that includes a User.
 *
 * After writing this I found play.api.mvc.Security.AuthenticatedRequest.
 * There's some nice ideas there, and some better design than here (like
 * providing the user resolver function to a constructor and making the action
 * a class instantiated into an object).
 *
 * This shows how to do this from the ground up and I like it's
 * relative simplicity. But readers should be warned that there seem to
 * be lots of building blocks in the play code itself ... I'm just not
 * sure how to use them.
 *
 */
case class AuthenticatedRequest[A](user: User, token: String, request: Request[A]) extends WrappedRequest(request)


trait AuthenticatedRequests {
  val serverSideSessionTokenKeyName = "token"
  val serverSideSessionExpirySec = Some(60 * 60 * 24)
  val loginDataForm = Form(
    mapping(
      "name" -> text,
      "password" -> text
    )(LoginData.apply)(LoginData.unapply)
  )

  /**
   * Override this to handle the situation that the user needs to authenticate for
   * some reason, such as their previous session has expired.
   */
  def authenticationRequired[A](request: Request[A]): Future[SimpleResult]

  /**
   * Optionally override this to handle the specific case that there is no session
   * at all for the user. This is optional because it calls authenticationRequired
   * by default.
   */
  def authenticationSessionMissing[A](request: Request[A]) = authenticationRequired(request)

  /**
   * Optionally override this to handle the specific case that the session for a
   * user has expired, e.g. they logged in two weeks ago.
   *
   * This is optional because it calls authenticationRequired by default.
   */
  def authenticationSessionExpired[A](request: Request[A]) = authenticationRequired(request)

  /**
   * Override this to handle the case that the login form data was invalid, e.g. missing
   * user or password.
   */
  def authenticationFormFailure[A](loginFormWithErrors: Form[LoginData])(request: Request[A]): Future[SimpleResult]

  /**
   * Override this to handle the case that the user/password combination were invalid.
   */
  def authenticationFailure[A](loginData: LoginData)(request: Request[A]): Future[SimpleResult]

  /**
   * This is invoked when the user authentication has succeeded and a new ServerSideSessionState
   * has been created and needs to be persisted to the server-side session store.
   *
   * That storage is what permits subsequent page loads to a protected area to succeed without
   * requiring the user to authenticate again.
   */
  def startSession[A](user: User, request: Request[A]): AuthenticatedRequest[A] = {
    val token = ServerSideSessions.create(ServerSideSessionState(user), serverSideSessionExpirySec)
    //request.session + (serverSideSessionTokenKeyName, token)
    println(s"XXX startSession token $token")
    AuthenticatedRequest[A](user, token, request)
  }

  /**
   * This is invoked to find an existing server-side session for the given request. If the
   * system finds a session it retrieves the user, and if the user data is present then
   * an AuthenticatedRequest is created and then provided downstream. This retrieval is
   * what allows page loads to protected areas to succeed without requiring the user to
   * authenticate each time.
   */
  def getSession[A](request: Request[A]): Either[Exception, AuthenticatedRequest[A]] = {
    println("XXX getSession: token->" + request.session.get(serverSideSessionTokenKeyName))
    for {
      token <- request.session.get(serverSideSessionTokenKeyName).toRight(new NoSessionError).right
      session <- ServerSideSessions.get(token).toRight(new ExpiredSessionError).right
    } yield {
      AuthenticatedRequest[A](session.user, token, request)
    }
  }

  /**
   * This is invoked to delete any existing server-side session, e.g. when logging out.
   */
  def abandonSession[A](request: Request[A]): Request[A] = {
    println("XXX abandonSession: "+request.session.get(serverSideSessionTokenKeyName))
    request.session.get(serverSideSessionTokenKeyName).map(ServerSideSessions.delete)
    request
  }

  /**
   * Apply the RequireAuthentication action to any page to require an authentication
   * check to be applied.
   *
   * If an existing server-side session is found - i.e. the user
   * has successfully authenticated previously and recently - that is loaded and
   * passed to the page in the form of an AuthenticatedRequest.
   *
   * If no such server-side session is found, or it has expired, then the user is
   * required to authenticate again. Controllers can control how that authentication
   * is initiated by providing implementations of the authenticationRequired method,
   * e.g. redirecting to a login page.
   */
  object RequireAuthentication extends ActionBuilder[AuthenticatedRequest] {
    def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {
      getSession(request) match {
        case Right(request: AuthenticatedRequest[A]) => block(request)
        case Left(e: NoSessionError)      => authenticationSessionMissing(request)
        case Left(e: ExpiredSessionError) => authenticationSessionExpired(request)
        case Left(other: Throwable)       => throw other
      }
    }
  }

  /**
   * Apply the AbandonAuthentication action to a "log out" Controller page and any
   * existing server-side session associated with the request is deleted. Subsequent
   * loads of any page will not locate a cached server-side session state and the
   * user will be considered "logged out".
   */
  object AbandonAuthentication extends ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: Request[A] => Future[SimpleResult]): Future[SimpleResult] = {
      block(abandonSession(request))
    }
  }

  /**
   * Apply the PerformAuthentication action to a Controller page and the login data
   * is checked, authentication performed. If authentication passes, server-side
   * session state is cached and the page invoked with a new AuthenticatedRequest.
   */
  object PerformAuthentication extends ActionBuilder[AuthenticatedRequest] {
    def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {

      loginDataForm.bindFromRequest()(request).fold(

        // when the form is not valid:
        formWithErrors => authenticationFormFailure(formWithErrors)(request), // Future.successful(BadRequest(formWithErrors.errorsAsJson)),

        // when the form is valid, authenticate and invoke the next processing block
        // (unless the authentication fails, in which case return a 401 status):
        loginData => MockUserService.authenticate(loginData.name, loginData.password).map { user =>
          abandonSession(request) // just in case
          block(startSession(user, request)) // new AuthenticatedRequest(user, request))
        } getOrElse {
          authenticationFailure(loginData)(request)
        }
      )
    }
  }
}