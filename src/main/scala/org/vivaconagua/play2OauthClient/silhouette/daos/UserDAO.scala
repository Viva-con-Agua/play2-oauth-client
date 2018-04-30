package scala.org.vivaconagua.play2OauthClient.silhouette.daos

import java.util.UUID
import scala.concurrent.Future
import scala.org.vivaconagua.play2OauthClient.silhouette.User

trait UserDAO {
  def findByUUID(uuid: UUID) : Future[Option[User]]
}
