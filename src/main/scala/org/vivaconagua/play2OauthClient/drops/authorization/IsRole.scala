package org.vivaconagua.play2OauthClient.drops.authorization

import java.util.UUID

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import org.vivaconagua.play2OauthClient.silhouette.{SpecialRole, User}
import play.api.mvc.Request

import scala.concurrent.Future

trait VolunteerManagerFinder {
  def check(user: User, checker: SpecialRole => Boolean): Boolean = user.roles.foldLeft(false)((isVM, role) =>
    role match {
      case special : SpecialRole => isVM || checker(special)
      case _ => isVM
    })
}

case class IsVolunteerManager(crewId: Option[UUID] = None, crewName: Option[String] = None) extends
  Authorization[User, CookieAuthenticator] with CombinableRestriction with VolunteerManagerFinder {

  override def isAuthorized[B](identity: User, authenticator: CookieAuthenticator)(implicit request: Request[B]): Future[Boolean] =
    Future.successful(
      check(identity, (role: SpecialRole) =>
        role.name == "VolunteerManager" &&
          crewId.flatMap(requiredCrewId => role.crewId.map(_ == requiredCrewId)).getOrElse(true) &&
          crewName.flatMap(requiredCrewName => role.crewName.map(_ == requiredCrewName)).getOrElse(true)
      )
    )
}

object IsVolunteerManager {
  def apply : IsVolunteerManager = IsVolunteerManager(None, None)
  def apply(crewId: UUID): IsVolunteerManager = IsVolunteerManager(Some(crewId), None)
  def apply(crewName: String): IsVolunteerManager = IsVolunteerManager(None, Some(crewName))
}

case class IsResponsibleFor(pillar: String) extends
  Authorization[User, CookieAuthenticator] with CombinableRestriction with VolunteerManagerFinder {

  override def isAuthorized[B](identity: User, authenticator: CookieAuthenticator)(implicit request: Request[B]): Future[Boolean] =
    Future.successful(check(identity, (role: SpecialRole) => role.pillar.map(_ == pillar).getOrElse(false)))
}

case class IsRole(name: String) extends Authorization[User, CookieAuthenticator] with CombinableRestriction {

  override def isAuthorized[B](identity: User, authenticator: CookieAuthenticator)(implicit request: Request[B]): Future[Boolean] =
    Future.successful(identity.roles.find(role => role.name == name).isDefined)
}

object IsAdmin extends IsRole("admin")
object IsEmployee extends IsRole("employee")
object IsSupporter extends IsRole("supporter")