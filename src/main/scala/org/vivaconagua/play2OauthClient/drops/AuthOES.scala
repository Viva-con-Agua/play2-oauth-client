package org.vivaconagua.play2OauthClient.drops

import javax.inject._
import play.api.inject.ApplicationLifecycle
import java.util.{Properties, UUID}
import org.vivaconagua.play2OauthClient.silhouette.User

import org.nats._
import akka.actor._

import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport

import scala.concurrent.duration._
import scala.concurrent.Future

import play.api.Configuration
import play.api.Logger


class AuthOESModule extends AbstractModule with AkkaGuiceSupport {
  def configure = {
    bind(classOf[AuthOES]).to(classOf[AuthOESImpl])
    bindActor[AuthOESHandler]("auth-oes-actor")
  }
}

trait AuthOES {
  def sendActor : ActorRef
}

@Singleton
class AuthOESImpl @Inject() (@Named("auth-oes-actor") authOESActor: ActorRef, conf : Configuration, lifecycle: ApplicationLifecycle) extends AuthOES {

  val logger: Logger = Logger(this.getClass())

  val nats : OESConnection = new OESConnection(authOESActor, conf)

  lifecycle.addStopHook { () =>
    // previous contents of Plugin.onStop
    Future.successful(nats.stop)
  }

  override def sendActor : ActorRef = authOESActor

}

/**
  * Holds the nats connection and subscribes the considered events.
  *
  * @author Johann Sell
  * @param conf Play configuration, required to read the nats endpoint
  */
class OESConnection(authOESActor: ActorRef, conf : Configuration) {

  val logger: Logger = Logger(this.getClass())

  private val server = conf.get[String]("nats.endpoint")
  private val opts: Properties = new Properties
  opts.put("servers", server)

  // create a connection
  private val natsConn : Option[Conn] = init(authOESActor)


  private def init(authOESActor: ActorRef) = {
    val connection = Conn.connect(this.opts)
    // subscribes the LOGOUT event. The handler parses the body as UUID and sends an AuthLogout event containing the UUID.
    connection.subscribe("LOGOUT", (msg: Msg) =>
      authOESActor ! AuthOESHandler.AuthLogout(UUID.fromString(msg.body))
    )
    // subscribes the LOGOUT event. The handler parses the body as UUID and sends an AuthLogout event containing the UUID.
    connection.subscribe("USER.DELETE", (msg: Msg) =>
      authOESActor ! AuthOESHandler.UserDelete(UUID.fromString(msg.body))
    )
    Some(connection)
  }

  /**
    * Stops the nats connection
    * @author Johann Sell
    */
  def stop(): Unit = {
    natsConn.map(_.close())
  }
}

/**
  * Companion object for AuthOES actor class.
  * Contains the protocol.
  *
  * @author Johann Sell
  */
object AuthOESHandler {
  def props = Props[AuthOESHandler]

  case class AuthLogoutTimerKey(id: UUID)
  /**
    * Represents a LOGOUT event, received using nats.
    *
    * @author Johann Sell
    * @param id identifies the user that has logged out.
    */
  case class AuthLogout(id: UUID)

  /**
    * Represents a USER.DELETE event, received using nats.
    *
    * @author Johann Sell
    * @param id identifies the user that has been deleted.
    */
  case class UserDelete(id: UUID)

  case class ReleaseTimerKey(id: UUID)
  case class ReleaseLogout(id: UUID)

  /**
    * Represents a questions if a user is logged out.
    * Implement to use the received LOGOUT events from nats inside session handling.
    *
    * @author Johann Sell
    * @param user represents the User.
    */
  case class IsLoggedOut(user: User)
}

/**
  * Implements the actor.
  *
  * @author Johann Sell
  * @param nats Handles the connection to nats.
  */
@Singleton
class AuthOESHandler @Inject() (conf : Configuration) extends Actor with Timers {
  import AuthOESHandler._

  val logger: Logger = Logger(this.getClass())

  // list of all received ids of logged out users
  private var loggedOutUserIds : List[UUID] = Nil

  // Time until a users ID will be deleted from loggedOutUserIds
  val sessionTimeout : Long = conf.getMillis("silhouette.authenticator.authenticatorIdleTimeout")//.getOrElse(60000 * 10)

  /**
    * Handler for different messages.
    *
    * @author Johann Sell
    * @return
    */
  def receive = {
    case AuthLogout(id : UUID) => {
      // add user id to list of logged out users
      this.loggedOutUserIds = this.loggedOutUserIds :+ id

      // assume a logout after x milliseconds
      timers.startSingleTimer(ReleaseTimerKey(id), ReleaseLogout(id), Duration(sessionTimeout, MILLISECONDS))
    }
    case UserDelete(id: UUID) => {
      // if a user has been deleted, s/he has to be logged out
      timers.startSingleTimer(AuthLogoutTimerKey(id), AuthLogout(id), Duration(0, MILLISECONDS))
    }
    case ReleaseLogout(id : UUID) => {
      this.loggedOutUserIds = this.loggedOutUserIds.filter(_ != id)
    }
    case IsLoggedOut(user: User) => {
      val isLoggedOut = this.loggedOutUserIds.contains(user.uuid)
      if(isLoggedOut) {
        // release the saved UUID, because otherwise you can run in a deadlock if somebody tries to re-login
        timers.startSingleTimer(ReleaseTimerKey(user.uuid), ReleaseLogout(user.uuid), Duration(0, MILLISECONDS))
      }
      // answer the question if the given user is logged out
      sender() ! isLoggedOut
    }
  }
}