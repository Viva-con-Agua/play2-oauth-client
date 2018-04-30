package scala.org.vivaconagua.play2OauthClient.silhouette

import play.api.libs.json._

/**
  * Created by johann on 13.12.16.
  */
case class AccessToken(tokenType : String, content: String, expiresIn: Long, refreshToken: String)

object AccessToken {
  def apply(json : JsValue) : AccessToken =
    AccessToken(
      (json \ "token_type").as[String],
      (json \ "access_token").as[String],
      (json \ "expires_in").as[Long],
      (json \ "refresh_token").as[String]
    )
}
