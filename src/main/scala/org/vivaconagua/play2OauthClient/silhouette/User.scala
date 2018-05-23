package org.vivaconagua.play2OauthClient.silhouette

import java.util.UUID
import play.api.libs.json._
import com.mohiva.play.silhouette.api.Identity

case class User(uuid: UUID) extends Identity

object User {
  implicit val userFormat : Format[User] = Json.format[User]
}
