# play2-oauth-client
A [Play2](https://www.playframework.com/) module implementing a small extension to the [silhouette](https://www.silhouette.rocks/) authorization library. Using the modules developers are able to connect to an [OAuth2](https://oauth.net/2/) provider that has been implemented to fit the needs of [Viva con Agua](https://www.vivaconagua.org/home).

Implements an OAuth2 client for Play2 apps. It allows to use [Drops](https://github.com/Viva-con-Agua/drops) as social provider and tailors the OAuth2 handshake to specific organizational support.
First, a user has to be logged out if and only if, a user has been logged out from Drops. Thus play2-oauth-client 
implements an additional [Object-Event-System (OES)](http://wiki.vivaconagua.org/Business_Object_Exchange) using the 
[nats](https://nats.io/) message broker.

## Usage
Resolve the library from Sonatype in your `build.sbt`:
```scala
resolvers += "Sonatype OSS Releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"

libraryDependencies += "org.vivaconagua" %% "play2-oauth-client" % "0.2.0"
```

You have to use the lib in a controller. You can simply implement the `DropsLoginController` trait:
```scala
package controllers

import javax.inject.Inject
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.SocialProviderRegistry
import org.vivaconagua.play2OauthClient.silhouette.CookieEnv
import org.vivaconagua.play2OauthClient.controller.DropsLoginController
import org.vivaconagua.play2OauthClient.silhouette.UserService
import concurrent.ExecutionContext.Implicits.global
import play.api.mvc._
import play.api.libs.ws._
import play.api._
import play.api.cache.CacheApi

class DropsController @Inject()(
                                ws: WSClient,
                                override val conf : Configuration,
                                cc: ControllerComponents,
                                override val silhouette: Silhouette[CookieEnv],
                                override val userService: UserService,
                                override val authInfoRepository: AuthInfoRepository,
                                override val socialProviderRegistry: SocialProviderRegistry,
                                cache: CacheApi
                              ) extends AbstractController(cc) with DropsLoginController {
  override val defaultRedirectUrl = routes.HomeController.index.url // defines the default page a user sees after login 
}
```

Now you have to add the following routes to your  `conf/routes` file. These routes are needed to redirect the users 
client in order to fulfill the OAuth2 handshake with the social provider [Drops](https://github.com/Viva-con-Agua/drops):
```scala

GET        /authenticate/:provider controllers.DropsController.authenticate(provider, route: Option[String])
POST       /authenticate/:provider controllers.DropsController.authenticate(provider, route: Option[String] = None)
```

Since play2-oauth-client has to communicate with your [nats](https://nats.io/) message broker and [Drops](https://github.com/Viva-con-Agua/drops)
you have to add the following lines to your `conf/application.conf`:
```scala
nats.endpoint="nats://<nats_ip>:<nats_port>" // default port is 4222

ms.name="<your_ms_name>" // example: BLOOB
ms.host="<your_ms_domain>" // example: http://localhost:9000
ms.entrypoint="<your_ms_route>" // the route that you have configured before, example: /authenticate/drops
drops.url.base="<drops_domain>" // example: http://localhost:9100 or https://pool.vivaconagua.org/drops
drops.client_id="<your_ms_id>" // the id that has been configured in drops to identify your microservice
drops.client_secret="<your_ms_secret>" // the secret that has been configured in drops to identify your microservice

play.filters.enabled += org.vivaconagua.play2OauthClient.drops.AuthOESFilter
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