package org.vivaconagua.play2OauthClient.silhouette

import java.util.UUID
import play.api.libs.json._
import com.mohiva.play.silhouette.api.Identity

trait Role {
  val name : String
}

object Role {
  implicit val roleJsonWrites = Writes[Role] {
    case gr: GeneralRole => GeneralRole.generalRoleJsonFormat.writes(gr)
    case sr: SpecialRole => SpecialRole.specialRoleJsonFormat.writes(sr)
  }

  implicit val roleJsonReads = Reads[Role] {
    case json: JsObject => (json \ "crewName").toOption match {
      case Some( _ ) => SpecialRole.specialRoleJsonFormat.reads(json)
      case _ => GeneralRole.generalRoleJsonFormat.reads(json)
    }
    case _ => JsError()//ContextFreeRole.contextFreeRoleJsonFormat.reads(Json.toJson[Role](ContextFreeRole( "" )))
  }
}

case class GeneralRole(name: String) extends Role
object GeneralRole {
  implicit val generalRoleJsonFormat = Json.format[GeneralRole]
}
case class SpecialRole(name: String, crewName: Option[String], pillar: Option[String]) extends Role
object SpecialRole {
  implicit val specialRoleJsonFormat = Json.format[SpecialRole]
}

case class User(uuid: UUID, roles: List[Role]) extends Identity {

  private def getSpecialRole = this.roles.find(_.name == "VolunteerManager").flatMap(_ match {
    case vm : SpecialRole => Some(vm)
    case _ => None
  })

  private def checkPillar(pillar: String) = this.getSpecialRole match {
    case Some(special) => special.pillar.exists(_ == pillar)
    case None => false
  }

  def isVolunteerManager = this.roles.filter(_.name == "VolunteerManager").nonEmpty
  def isAdmin = this.roles.filter(_.name == "admin").nonEmpty
  def isEmployee = this.roles.filter(_.name == "employee").nonEmpty

  def isOnlyVolunteer = isVolunteerManager && !(isAdmin || isEmployee)

  def isFinance = checkPillar("finance")
  def isAction = checkPillar("operation")
  def isNetwork = checkPillar("network")
  def isEducation = checkPillar("education")

  def getCrew : Option[UUID] = this.getSpecialRole.flatMap(_.crewId)
}

object User {
  implicit val userFormat : Format[User] = Json.format[User]
}
