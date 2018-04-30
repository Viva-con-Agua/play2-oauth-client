package scala.org.vivaconagua.play2OauthClient.drops

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.exceptions.ProfileRetrievalException
import DropsProvider._
import com.mohiva.play.silhouette.impl.providers._
import play.api.libs.json.JsValue

//import play.api.mvc._

import scala.concurrent.Future

/**
  * Implements a basic provider to integrate Drops as OAuth2 provider for [Silhouette](https://www.silhouette.rocks/).
  * Oriented at: https://github.com/mohiva/play-silhouette/blob/master/silhouette/app/com/mohiva/play/silhouette/impl/providers/oauth2/InstagramProvider.scala
  *
  * @author Johann Sell
  */
trait BaseDropsProvider extends OAuth2Provider {
  /**
    * The content type to parse a profile from.
    */
  override type Content = JsValue

  /**
    * The provider ID.
    */
  override val id = ID

  /**
    * Defines the URLs that are needed to retrieve the profile data.
    */
  override protected val urls = Map("api" -> settings.apiURL.getOrElse(API))

  /**
    * Builds the social profile.
    *
    * @param authInfo The auth info received from the provider.
    * @return On success the build social profile, otherwise a failure.
    */
  override protected def buildProfile(authInfo: OAuth2Info): Future[Profile] = {
    httpLayer.url(urls("api").format(authInfo.accessToken)).get().flatMap { response =>
      val json = response.json
      (json \ "code").asOpt[Int] match {
        case Some(code) if code != 200 =>
          val errorType = (json \ "type").asOpt[String]
          val errorMsg = (json \ "msg").asOpt[String]

          throw new ProfileRetrievalException(SpecifiedProfileError.format(id, code, errorType, errorMsg))
        case _ => profileParser.parse(json, authInfo)
      }
    }
  }
}

/**
  * The profile parser for the drops social profile.
  */
class DropsProfileParser extends SocialProfileParser[JsValue, DropsSocialProfile, OAuth2Info] {

  /**
    * Parses the social profile.
    *
    * @param json     The content returned from the provider.
    * @param authInfo The auth info to query the provider again for additional data.
    * @return The social profile from given result.
    */
  override def parse(json: JsValue, authInfo: OAuth2Info) = Future.successful {
    val userID = (json \ "id").as[String]
    val email = (json \\ "email").map(_.as[String]).toSet
    val firstName = (json \\ "firstName").map(_.as[String]).headOption
    val lastName = (json \\ "lastName").map(_.as[String]).headOption
    val fullName = (json \\ "fullName").map(_.as[String]).headOption
    val avatarURL = (json \\ "avatarUrl").map(_.as[String]).headOption

    DropsSocialProfile(
      loginInfo = LoginInfo(ID, userID),
      firstName = firstName.get,
      lastName = lastName.get,
      fullName = fullName.get,
      email = email,
      avatarURL = avatarURL)
  }
}

case class DropsSocialProfile(
                               loginInfo: LoginInfo,
                               firstName: String,
                               lastName: String,
                               fullName: String,
                               email: Set[String],
                               avatarURL: Option[String] = None) extends SocialProfile

/**
  * The profile builder for the drops social profile.
  */
trait DropsSocialProfileBuilder {
  self: SocialProfileBuilder =>

  /**
    * The type of the profile a profile builder is responsible for.
    */
  type Profile = DropsSocialProfile
}

/**
  * The Drops OAuth2 Provider.
  *
  * @param httpLayer     The HTTP layer implementation.
  * @param stateHandler  The state provider implementation.
  * @param settings      The provider settings.
  */
class DropsProvider(
                         protected val httpLayer: HTTPLayer,
                         protected val stateHandler: SocialStateHandler,
                         val settings: OAuth2Settings)
  extends BaseDropsProvider with DropsSocialProfileBuilder {

  /**
    * The type of this class.
    */
  override type Self = DropsProvider

  /**
    * The profile parser implementation.
    */
  override val profileParser = new DropsProfileParser

  /**
    * Gets a provider initialized with a new settings object.
    *
    * @param f A function which gets the settings passed and returns different settings.
    * @return An instance of the provider initialized with new settings.
    */
  override def withSettings(f: (Settings) => Settings) = new DropsProvider(httpLayer, stateHandler, f(settings))
}

/**
  * The companion object.
  */
object DropsProvider {

  /**
    * The error messages.
    */
  val SpecifiedProfileError = "[Silhouette][%s] Error retrieving profile information. Error code: %s, type: %s, message: %s"

  /**
    * The Drops constants.
    */
  val ID = "drops" // TODO: Read from config!
  val API = "https://pool.vivaconagua.org/drops/oauth2/rest/profile?access_token=%s"
}