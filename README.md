# play2-oauth-client
Implements an OAuth2 client for Play 2 apps. It allows to use Drops as social provider and tailors the OAuth2 handshake to specific organizational support.

A [Play2](https://www.playframework.com/) module implementing a small extension to the [silhouette](https://www.silhouette.rocks/) authorization library. Using the modules developers are able to connect to an [OAuth2](https://oauth.net/2/) provider that has been implemented to fit the needs of [Viva con Agua](https://www.vivaconagua.org/home).

## Usage

In `conf/routes`:
```scala

GET        /authenticate/:provider controllers.LoginController.authenticate(provider, route: Option[String])
POST       /authenticate/:provider controllers.LoginController.authenticate(provider, route: Option[String] = None)
```

Wichtige Config:
```scala
nats.endpoint="nats://0.0.0.0:4222"

ms.name="BLOOB"
ms.host="http://localhost:9000"
ms.entrypoint="/authenticate/drops"
drops.url.base="http://localhost:9100"
drops.client_id="<your_ms_id>"
drops.client_secret="<your_ms_secret>"
```

A possible implementation for a controller handling the authentication:
```scala
import javax.inject.Inject

import com.mohiva.play.silhouette.api.{Silhouette,LoginEvent}
import com.mohiva.play.silhouette.api.exceptions.{ProviderException,AuthenticatorException,CryptoException}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.state._
import com.mohiva.play.silhouette.impl.exceptions._
import org.vivaconagua.play2OauthClient.silhouette.UserService
import org.vivaconagua.play2OauthClient.silhouette.daos.drops.{UserDAOHTTPMethodException,UserDAONetworkException}
import org.vivaconagua.play2OauthClient.silhouette.AccessToken

import scala.concurrent.ExecutionContext.Implicits.global
import org.vivaconagua.play2OauthClient.silhouette.CookieEnv
import org.vivaconagua.play2OauthClient.drops.DropsSocialProfileBuilder

import scala.concurrent.Future

import play.api.mvc._
import play.api.libs.ws._
import play.api._

import play.api.libs.json._

import play.api.cache.CacheApi
import play.api.i18n.{ I18nSupport, Messages }

class LoginController @Inject()(
                                 ws: WSClient,
                                 conf : Configuration,
                                 cc: ControllerComponents,
                                 silhouette: Silhouette[CookieEnv],
                                 userService: UserService,
                                 authInfoRepository: AuthInfoRepository,
                                 socialProviderRegistry: SocialProviderRegistry,
                                 cache: CacheApi
                               ) extends AbstractController(cc) with I18nSupport {

  val logger: Logger = Logger(this.getClass())
  val dropsLogin = conf.getString("drops.url.base").get + conf.getString("drops.url.login").get

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
              token, Redirect(userState.state.get("route").getOrElse(routes.HomeController.index.url))
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
```

As an alternative, you can simply implement the `DropsLoginController` trait:
```scala
import javax.inject.Inject

import org.vivaconagua.controller.DropsLoginController
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.mvc._
import play.api.libs.ws._
import play.api._
import play.api.cache.CacheApi

class HomeController @Inject()(
                                              ws: WSClient,
                                              override val conf : Configuration,
                                              cc: ControllerComponents,
                                              override val silhouette: Silhouette[CookieEnv],
                                              override val userService: UserService,
                                              override val authInfoRepository: AuthInfoRepository,
                                              override val socialProviderRegistry: SocialProviderRegistry,
                                              cache: CacheApi
                                            ) extends AbstractController(cc) with DropsLoginController {
  override val defaultRedirectUrl = routes.HomeController.index.url 
}
```

An example controller using the implemented authentification:

```scala
import javax.inject.Inject

import play.api.mvc._
import com.mohiva.play.silhouette.api.Silhouette
import org.vivaconagua.play2OauthClient.silhouette.CookieEnv
import org.vivaconagua.play2OauthClient.silhouette.UserService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.mvc.AnyContent
import play.api.Logger

/**
  * A very small controller that renders a home page.
  */
class HomeController @Inject()(
                                cc: ControllerComponents,
                                silhouette: Silhouette[CookieEnv],
                                userService: UserService
) extends AbstractController(cc) {

  val logger: Logger = Logger(this.getClass())

  def userTest = silhouette.SecuredAction.async { implicit request => {
    Future.successful(Ok("User: " + request.identity))
  }}
}
```

## How it works
Todo

## ChangeLog

### Version 0.2.0 (2018-05-03)

* [[F] - Session management](https://github.com/Viva-con-Agua/play2-oauth-client/issues/2)
* [[F] - OAuth2 Drops Client](https://github.com/Viva-con-Agua/play2-oauth-client/issues/1)