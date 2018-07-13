package org.vivaconagua.play2OauthClient.controller

import com.mohiva.play.silhouette.api.{Silhouette,LoginEvent}
import com.mohiva.play.silhouette.api.exceptions.{ProviderException,AuthenticatorException,CryptoException}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.state._
//import com.mohiva.play.silhouette.impl.exceptions._
//import com.mohiva.play.silhouette.api.actions.SecuredErrorHandler
import org.vivaconagua.play2OauthClient.drops.DropsProvider
import org.vivaconagua.play2OauthClient.silhouette.UserService
import org.vivaconagua.play2OauthClient.silhouette.daos.drops.{UserDAOHTTPMethodException,UserDAONetworkException}
//import scala.org.vivaconagua.play2OauthClient.silhouette.AccessToken

import org.vivaconagua.play2OauthClient.silhouette.CookieEnv
import org.vivaconagua.play2OauthClient.drops.DropsSocialProfileBuilder

import scala.concurrent.Future

import play.api.mvc._
//import play.api.mvc.Results._
//import play.api.libs.ws._
import play.api._

import play.api.libs.json._

import play.api.i18n.{ I18nSupport, Messages }

import scala.concurrent.ExecutionContext.Implicits.global

trait DropsLoginController extends AbstractController with I18nSupport {

  val conf: Configuration
  val socialProviderRegistry: SocialProviderRegistry
  val userService : UserService
  val authInfoRepository: AuthInfoRepository
  val silhouette: Silhouette[CookieEnv]
  val logger: Logger = Logger(this.getClass())
  val dropsLogin = conf.get[String]("drops.url.base") + conf.get[String]("drops.url.login")

  val defaultRedirectUrl: String

  /**
    * Authenticates a user against a social provider.
    *
    * @param provider The ID of the provider to authenticate against.
    * @return The result to display.
    */
  def authenticate(provider: String, route: Option[String], ajax: Option[Boolean]) = Action.async { implicit request =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialStateProvider with DropsSocialProfileBuilder) => {
        val state = route.map((r) => UserStateItem(Map("route" -> r))).getOrElse(UserStateItem(Map()))
        // Generate a new provider considering the `ajax` flag, used to signal [Drops] how to handle the case of no
        // authorized user (`Redirect` or JSON error message)
        val provider = (p match {
          case dropsP : DropsProvider => dropsP.withSettings(settings =>
            ajax.map((flag) => settings.copy(authorizationParams = Map("ajax" -> flag.toString()))).getOrElse(settings)
          )
          case _ => p
        })
        provider.authenticate(state).flatMap {
          case Left(result) => Future.successful(result)
          case Right(StatefulAuthInfo(authInfo, userState)) => for {
            profile <- provider.retrieveProfile(authInfo)
            user <- userService.retrieve(profile.loginInfo)
            authInfo <- authInfoRepository.save(profile.loginInfo, authInfo)
            authenticator <- silhouette.env.authenticatorService.create(profile.loginInfo)
            token <- silhouette.env.authenticatorService.init(authenticator)
            result <- silhouette.env.authenticatorService.embed(
              token, Redirect(userState.state.get("route").getOrElse(defaultRedirectUrl))
            )
          } yield {
            user match {
              case Some(u) => {
                silhouette.env.eventBus.publish(LoginEvent(u, request))
                result
              }
              case _ => {
                val key = profile.loginInfo.providerKey
                logger.error("Unexpected provider error", new ProviderException(s"Found no user for given LoginInfo key $key"))
                Redirect(dropsLogin).flashing("error" -> Messages("silhouette.error.could.not.authenticate"))
              }
            }
          }
        } recover {
          // other exceptions (NotAuthenticatedException, NotAuthorizedException) will be catched by [DropsSecuredErrorHandler]
          case e: ProviderException => {
            logger.error("ProviderException", e)
            Redirect(dropsLogin).flashing("error" -> Messages("silhouette.error." + e.getClass.getSimpleName))
          }
          case e: AuthenticatorException => {
            logger.error("AuthenticatorException", e)
            Redirect(dropsLogin).flashing("error" -> Messages("silhouette.error." + e.getClass.getSimpleName))
          }
          case e: CryptoException => {
            logger.error("AuthenticatorException", e)
            Redirect(dropsLogin).flashing("error" -> Messages("silhouette.error." + e.getClass.getSimpleName))
          }
          case e: UserDAONetworkException => {
            logger.error("UserDAOException", e)
            Redirect(dropsLogin).flashing("error" -> Messages("drops.dao.error.network"))
          }
          case e: UserDAOHTTPMethodException => {
            logger.error("UserDAOException", e)
            Redirect(dropsLogin).flashing("error" -> Messages("drops.dao.error.method"))
          }
        }
      }
      case _ => {
        logger.error("Unexpected provider error", new ProviderException(s"Cannot authenticate with unexpected social provider $provider"))
        Future.successful(Redirect(dropsLogin).flashing("error" -> Messages("silhouette.error.could.not.authenticate")))
      }
    })
  }

  /**
    * JSON interface to request the currently logged in user. If there is no user session, the `Action` will intiate the
    * OAuth2 handshake with [Drops] by redirecting to [DropsLoginController.authenticate] with `ajax` flag set to `true`.
    * Thus, [Drops] knows to send a JSON error instead of redirecting to a login page, if there is also no authorized
    * user.
    *
    * @author Johannn Sell
    * @return
    */
  def frontendLogin = silhouette.UserAwareAction.async { implicit request =>
    request.identity match {
      case Some(user) => Future.successful(Ok(Json.toJson(user)))
      case _ => Future.successful(Redirect(this.redirectToDrops(request, true)))
    }
  }

  /**
    * Generates the URL to initate the OAuth2 handshake.
    *
    * @author Johann Sell
    * @param request is used to redirect the user back to the originaly requested page after a successful handshake.
    * @param ajax indicates, if a login screen has to be shown or a JSON error message is needed as response.
    * @return
    */
  def redirectToDrops(request : RequestHeader, ajax: Boolean) =
    conf.get[String]("ms.host") + conf.get[String]("ms.entrypoint") + "?route=" + request.uri + "&ajax=" + ajax
}