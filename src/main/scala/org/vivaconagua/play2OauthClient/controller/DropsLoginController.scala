package org.vivaconagua.play2OauthClient.controller

import com.mohiva.play.silhouette.api.{Silhouette,LoginEvent}
import com.mohiva.play.silhouette.api.exceptions.{ProviderException,AuthenticatorException,CryptoException}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.state._
//import com.mohiva.play.silhouette.impl.exceptions._
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

//import play.api.libs.json._

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
  def authenticate(provider: String, route: Option[String]) = Action.async { implicit request =>
    (socialProviderRegistry.get[SocialProvider](provider) match {
      case Some(p: SocialStateProvider with DropsSocialProfileBuilder) => {
        val state = route.map((r) => UserStateItem(Map("route" -> r))).getOrElse(UserStateItem(Map()))
        p.authenticate(state).flatMap {
          case Left(result) => Future.successful(result)
          case Right(StatefulAuthInfo(authInfo, userState)) => for {
            profile <- p.retrieveProfile(authInfo)
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
}