package org.vivaconagua.play2OauthClient.silhouette.daos

import java.util.UUID
import scala.concurrent.Future
import org.vivaconagua.play2OauthClient.silhouette.User

trait UserDAO {
  def findByUUID(uuid: UUID) : Future[Option[User]]
}
