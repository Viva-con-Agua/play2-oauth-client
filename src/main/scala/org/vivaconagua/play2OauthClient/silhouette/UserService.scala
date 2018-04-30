package scala.org.vivaconagua.play2OauthClient.silhouette

import javax.inject.Inject
import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import daos.UserDAO
import play.api.Logger
import play.api.Configuration
import com.mohiva.play.silhouette.api.exceptions.ProviderException

import scala.concurrent.Future

/**
  * A custom user service which relies on the previous defined `User`.
  */
class UserService @Inject()(userDAO: UserDAO, conf : Configuration) extends IdentityService[User] {
  val logger: Logger = Logger(this.getClass())

  private val dropsProviderID = conf.get[String]("drops.oauth2.providerID")

  /**
    * Retrieves a user that matches the specified login info.
    *
    * @param loginInfo The login info to retrieve a user.
    * @return The retrieved user or None if no user could be retrieved for the given login info.
    */
  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = loginInfo.providerID match {
    case this.dropsProviderID => userDAO.findByUUID(UUID.fromString(loginInfo.providerKey))
    case _ => {
      val providerID = loginInfo.providerID
      logger.error("Unknown provider error", new ProviderException(s"Given provider id '$providerID' is unkown."))
      Future.successful(None)
    }
  }
}