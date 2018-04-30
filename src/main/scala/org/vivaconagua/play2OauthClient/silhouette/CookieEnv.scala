package scala.org.vivaconagua.play2OauthClient.silhouette

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator

/**
  * The default env.
  */
trait CookieEnv extends Env {
  type I = User
  type A = CookieAuthenticator
}