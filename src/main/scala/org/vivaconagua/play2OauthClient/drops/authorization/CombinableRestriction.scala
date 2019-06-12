package org.vivaconagua.play2OauthClient.drops.authorization

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import org.vivaconagua.play2OauthClient.silhouette.User
import play.api.mvc.Request

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class AuthAndCombination(one: CombinableRestriction, two: CombinableRestriction) extends Authorization[User,CookieAuthenticator] {
  override def isAuthorized[B](identity: User, authenticator: CookieAuthenticator)(implicit request: Request[B]): Future[Boolean] =
    one.isAuthorized(identity, authenticator).flatMap(
      first => two.isAuthorized(identity, authenticator).map(
        second => first && second
      )
    )
}

case class AuthOrCombination(one: CombinableRestriction, two: CombinableRestriction) extends Authorization[User,CookieAuthenticator] {
  override def isAuthorized[B](identity: User, authenticator: CookieAuthenticator)(implicit request: Request[B]): Future[Boolean] =
    one.isAuthorized(identity, authenticator).flatMap(
      first => two.isAuthorized(identity, authenticator).map(
        second => first || second
      )
    )
}

trait CombinableRestriction extends Authorization[User,CookieAuthenticator] {
  def isAuthorized[B](identity: User, authenticator: CookieAuthenticator)(implicit request: Request[B]): Future[Boolean]

  def &&(other: CombinableRestriction) : Authorization[User,CookieAuthenticator] = {
    AuthAndCombination(this, other)
  }

  def ||(other: CombinableRestriction) : Authorization[User,CookieAuthenticator] = {
    AuthOrCombination(this, other)
  }
}
