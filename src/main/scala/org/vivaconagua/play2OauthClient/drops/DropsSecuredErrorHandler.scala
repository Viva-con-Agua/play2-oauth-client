package scala.org.vivaconagua.play2OauthClient.drops

import javax.inject.Inject

import scala.concurrent.Future
import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import play.api.Configuration
//import play.api.http.ContentTypes
import play.api.i18n.{ I18nSupport, Messages, MessagesApi }
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results._

class DropsSecuredErrorHandler @Inject()(val messagesApi: MessagesApi, config: Configuration)
  extends SecuredErrorHandler
    with I18nSupport
    with RequestExtractors
    with Rendering {

  val refererHeader = "Referer"
  def redirectToDrops(request : RequestHeader) =
    config.get[String]("ms.host") + config.get[String]("ms.entrypoint") + "?route=" + request.uri
//    controllers.routes.LoginController.authenticate("drops", Some(request.uri))

  /**
    * Called when a user is not authenticated.
    *
    * Redirects to login using Drops as social provider. Cause, the microservice has no own authentification.
    *
    * Normally, as defined by RFC 2616, the status code of the response should be 401 Unauthorized.
    *
    * @param request The request header.
    * @return The result to send to the client.
    */
  override def onNotAuthenticated(implicit request: RequestHeader) = {
    Future.successful(Redirect(this.redirectToDrops(request)))
  }

  /**
    * Called when a user is authenticated but not authorized.
    *
    * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
    *
    * @param request The request header.
    * @return The result to send to the client.
    */
  override def onNotAuthorized(implicit request: RequestHeader) = {
    request.headers.get(refererHeader).map((referer) =>
      Future.successful(Redirect(referer).flashing("error" -> Messages("silhouette.access.denied")))
    ).getOrElse(
      produceResponse(Forbidden, Messages("silhouette.access.denied"))
    )
  }

  protected def produceResponse[S <: Status](status: S, msg: String)(
    implicit request: RequestHeader
  ): Future[Result] =
    Future.successful(render {
      case Accepts.Json() => status(toJsonError(msg))
      case _ => status(msg)
    })

  protected def toJsonError(message: String) =
    Json.obj(
      "status" -> "error",
      "message" -> message
    )
}