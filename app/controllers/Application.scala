package controllers

import play.api.mvc._


object Application extends Controller { //with AuthenticationActions with AuthenticatedSessionActions {

  val logger = play.Logger.of(getClass)

  def index = Action {
    Ok(views.html.index("This is a plain old index page."))
  }
}
