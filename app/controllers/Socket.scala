package controllers

import play.api.mvc._
import play.api.Play.current
import play.api.libs.json._


/**
 *
 */
object Socket extends Controller {
  /**
   *
   */
  def open = WebSocket.acceptWithActor[JsValue, JsValue] { implicit request => out =>
    Application.getLocalUser(request)
    Actors.SocketActor.props(out)
  }
}
