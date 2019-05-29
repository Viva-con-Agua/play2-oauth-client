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
case class SpecialRole(name: String, crewId:Option[UUID], crewName: Option[String], pillar: Option[String]) extends Role
object SpecialRole {
  implicit val specialRoleJsonFormat = Json.format[SpecialRole]
}

case class User(uuid: UUID, roles: List[Role]) extends Identity

object User {
  implicit val userFormat : Format[User] = Json.format[User]
}
