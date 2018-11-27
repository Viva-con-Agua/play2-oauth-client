package org.vivaconagua.play2OauthClient.silhouette.daos.drops

import java.util.UUID

import javax.inject.Inject
import org.vivaconagua.play2OauthClient.silhouette.{GeneralRole, SpecialRole}

import scala.concurrent.Future
//import scala.concurrent.duration._
//import scala.concurrent.ExecutionContext
import play.api.Configuration
import org.vivaconagua.play2OauthClient.silhouette.daos.UserDAO
import org.vivaconagua.play2OauthClient.silhouette.User
import play.api.libs.ws._
import play.api.libs.json._
import play.api.Logger
import scala.concurrent.ExecutionContext.Implicits.global

class UserDropsDAO @Inject() (ws: WSClient, conf : Configuration) extends UserDAO {
  val logger: Logger = Logger(this.getClass())

  val restEndpoint = conf.get[String]("drops.url.base") + conf.get[String]("drops.url.rest.user.path")
  val restMethod = conf.get[String]("drops.url.rest.user.method")

  val dropsClientId = conf.get[String]("drops.client_id")
  val dropsClientSecret = conf.get[String]("drops.client_secret")

  override def findByUUID(uuid: UUID) : Future[Option[User]] = {
    val url = ws.url(restEndpoint + uuid + "?client_id=" + dropsClientId + "&client_secret=" + dropsClientSecret)
    restMethod match {
      case "GET" => url.get().map(response => response.status match {
        case 200 => Some(toUser(response.json))
        case 404 => {
          logger.info("Requested user " + uuid + " not found!")
          None
        }
        case _ => throw UserDAONetworkException(response.json)
      })
      case "POST" => url.addHttpHeaders("Content-Type" -> "application/json", "Accept" -> "application/json").post(Json.obj()).map(response => response.status match {
        case 200 => Some(toUser(response.json))
        case 404 => {
          logger.info("Requested user " + uuid + " not found!")
          None
        }
        case _ => throw UserDAONetworkException(response.json)
      })
      case _ => throw UserDAOHTTPMethodException(restMethod)
    }
  }

  private def toUser(json: JsValue) : User = {
    val userRoles = (json \ "roles").validate[List[JsValue]].asOpt match {
      case Some(list) => list.map((role) => (role \ "role").validate[String].asOpt match {
        case Some(r) => Some(GeneralRole(r))
        case _ => None
      }).filter(_.isDefined).map(_.get)
      case _ => Nil
    }
    val supporterRoles = (json \ "profiles").validate[List[JsValue]].asOpt match {
      case Some(list) => list.flatMap((profile) =>
        (profile \ "supporter" \ "roles").validate[List[JsValue]].asOpt match {
          case Some(l) => l.map((role) => {
            SpecialRole(
              name = (role \ "name").as[String],
              crewName = (role \ "crew" \ "name").validate[String].asOpt,
              pillar = (role \ "pillar" \ "name").validate[String].asOpt
            )
          })
          case _ => Nil
        })
      case _ => Nil
    }

    User(
      uuid = (json \ "id").as[UUID],
      roles = userRoles ++ supporterRoles
    )
  }
}

case class UserDAOHTTPMethodException(method: String, cause: Throwable = null) extends
  Exception("HTTP method " + method + " is not supported.", cause)

case class UserDAONetworkException(status: Int, typ: String, msg: String, cause: Throwable = null) extends
  Exception(msg, cause)

object UserDAONetworkException {
  def apply(dropsResponse: JsValue) : UserDAONetworkException =
    UserDAONetworkException(
      status = (dropsResponse \ "code").as[Int],
      typ = (dropsResponse \ "type").as[String],
      msg = (dropsResponse \ "msg").as[String]
    )
}