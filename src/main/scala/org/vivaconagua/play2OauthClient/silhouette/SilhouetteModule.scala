package scala.org.vivaconagua.play2OauthClient.silhouette

import javax.inject._

import play.api._
import play.api.mvc.{CookieHeaderEncoding,SessionCookieBaker}
import play.api.libs.ws.WSClient

import com.google.inject.name.Named
import com.google.inject.{AbstractModule, Provides}
import net.codingwell.scalaguice.ScalaModule

import com.mohiva.play.silhouette.api.actions.{SecuredErrorHandler, UnsecuredErrorHandler}
import com.mohiva.play.silhouette.api.crypto._
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.services._
import com.mohiva.play.silhouette.api.util._
import com.mohiva.play.silhouette.api.{Environment, EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto.{JcaCrypter, JcaCrypterSettings, JcaSigner, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators._
import com.mohiva.play.silhouette.impl.providers._
//import com.mohiva.play.silhouette.impl.providers.oauth1._
//import com.mohiva.play.silhouette.impl.providers.oauth1.secrets.{CookieSecretProvider, CookieSecretSettings}
//import com.mohiva.play.silhouette.impl.providers.oauth1.services.PlayOAuth1Service
//import com.mohiva.play.silhouette.impl.providers.oauth2._
//import com.mohiva.play.silhouette.impl.providers.openid.YahooProvider
//import com.mohiva.play.silhouette.impl.providers.openid.services.PlayOpenIDService
import com.mohiva.play.silhouette.impl.providers.state.{CsrfStateItemHandler, CsrfStateSettings}
//import com.mohiva.play.silhouette.impl.services._
import com.mohiva.play.silhouette.impl.util._
import com.mohiva.play.silhouette.password.BCryptPasswordHasher//, BCryptSha256PasswordHasher}
import com.mohiva.play.silhouette.persistence.daos.{DelegableAuthInfoDAO, InMemoryAuthInfoDAO}
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.EnumerationReader._

import scala.concurrent.ExecutionContext.Implicits.global

import daos.UserDAO
import daos.drops.UserDropsDAO
import scala.org.vivaconagua.play2OauthClient.drops.{DropsProvider,DropsSecuredErrorHandler}

/** Sets up custom components for Play, including a social provider that handles the OAuth2 handshake with Drops.
  *
  * @author Johann Sell
  */
class SilhouetteModule
  extends AbstractModule
    with ScalaModule {

  /** Implements the bindings.*/
  override def configure() = {
    bind[UserDAO].to[UserDropsDAO].in[Singleton]
    bind[Silhouette[CookieEnv]].to[SilhouetteProvider[CookieEnv]]
    bind[UnsecuredErrorHandler].to[CustomUnsecuredErrorHandler]
    bind[SecuredErrorHandler].to[DropsSecuredErrorHandler]
    bind[CacheLayer].to[PlayCacheLayer]
    bind[IDGenerator].toInstance(new SecureRandomIDGenerator())
    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)
    bind[FingerprintGenerator].toInstance(new DefaultFingerprintGenerator(false))
    bind[EventBus].toInstance(EventBus())
    bind[Clock].toInstance(Clock())

    // Replace this with the bindings to your concrete DAOs
    bind[DelegableAuthInfoDAO[PasswordInfo]].toInstance(new InMemoryAuthInfoDAO[PasswordInfo])
    bind[DelegableAuthInfoDAO[OAuth1Info]].toInstance(new InMemoryAuthInfoDAO[OAuth1Info])
    bind[DelegableAuthInfoDAO[OAuth2Info]].toInstance(new InMemoryAuthInfoDAO[OAuth2Info])
    bind[DelegableAuthInfoDAO[OpenIDInfo]].toInstance(new InMemoryAuthInfoDAO[OpenIDInfo])

  }

  /**
    * Provides the HTTP layer implementation.
    *
    * @param client Play's WS client.
    * @return The HTTP layer implementation.
    */
  @Provides
  def provideHTTPLayer(client: WSClient): HTTPLayer = new PlayHTTPLayer(client)

  /**
    * Provides the Silhouette environment.
    *
    * @param userService The user service implementation.
    * @param authenticatorService The authentication service implementation.
    * @param eventBus The event bus instance.
    * @return The Silhouette environment.
    */
  @Provides
  def provideEnvironment(
                          userService: UserService,
                          authenticatorService: AuthenticatorService[CookieAuthenticator],
                          eventBus: EventBus): Environment[CookieEnv] = {

    Environment[CookieEnv](
      userService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  /**
    * Provides the social provider registry.
    *
    * @param dropsProvider The Drops provider implementation.
    * @return The Silhouette environment.
    */
  @Provides
  def provideSocialProviderRegistry(
                                     dropsProvider : DropsProvider,
                                     //                                     facebookProvider: FacebookProvider,
                                     //                                     googleProvider: GoogleProvider,
                                     //                                     vkProvider: VKProvider,
                                     //                                     twitterProvider: TwitterProvider,
                                     //                                     xingProvider: XingProvider
                                   ): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      dropsProvider
      //      googleProvider,
      //      facebookProvider,
      //      twitterProvider,
      //      vkProvider,
      //      xingProvider
    ))
  }

  /**
    * Provides the signer for the OAuth1 token secret provider.
    *
    * @param configuration The Play configuration.
    * @return The signer for the OAuth1 token secret provider.
    */
  @Provides @Named("oauth1-token-secret-signer")
  def provideOAuth1TokenSecretSigner(configuration: Configuration): Signer = {
    new JcaSigner(configuration.underlying.as[JcaSignerSettings]("silhouette.oauth1TokenSecretProvider.signer"))
  }

  /**
    * Provides the crypter for the OAuth1 token secret provider.
    *
    * @param configuration The Play configuration.
    * @return The crypter for the OAuth1 token secret provider.
    */
  @Provides @Named("oauth1-token-secret-crypter")
  def provideOAuth1TokenSecretCrypter(configuration: Configuration): Crypter = {
    new JcaCrypter(configuration.underlying.as[JcaCrypterSettings]("silhouette.oauth1TokenSecretProvider.crypter"))
  }
  /**
    * Provides the signer for the authenticator.
    *
    * @param configuration The Play configuration.
    * @return The signer for the authenticator.
    */
  @Provides @Named("authenticator-signer")
  def provideAuthenticatorSigner(configuration: Configuration): Signer = {
    new JcaSigner(configuration.underlying.as[JcaSignerSettings]("silhouette.authenticator.signer"))
  }

  /**
    * Provides the crypter for the authenticator.
    *
    * @param configuration The Play configuration.
    * @return The crypter for the authenticator.
    */
  @Provides @Named("authenticator-crypter")
  def provideAuthenticatorCrypter(configuration: Configuration): Crypter = {
    new JcaCrypter(configuration.underlying.as[JcaCrypterSettings]("silhouette.authenticator.crypter"))
  }

  /**
    * Provides the signer for the CSRF state item handler.
    *
    * @param configuration The Play configuration.
    * @return The signer for the CSRF state item handler.
    */
  @Provides @Named("csrf-state-item-signer")
  def provideCSRFStateItemSigner(configuration: Configuration): Signer = {
    new JcaSigner(configuration.underlying.as[JcaSignerSettings]("silhouette.csrfStateItemHandler.signer"))
  }

  /**
    * Provides the signer for the social state handler.
    *
    * @param configuration The Play configuration.
    * @return The signer for the social state handler.
    */
  @Provides @Named("social-state-signer")
  def provideSocialStateSigner(configuration: Configuration): Signer = {
    new JcaSigner(configuration.underlying.as[JcaSignerSettings]("silhouette.socialStateHandler.signer"))
  }

  /**
    * Provides the auth info repository.
    *
    * @param passwordInfoDAO The implementation of the delegable password auth info DAO.
    * @param oauth1InfoDAO The implementation of the delegable OAuth1 auth info DAO.
    * @param oauth2InfoDAO The implementation of the delegable OAuth2 auth info DAO.
    * @param openIDInfoDAO The implementation of the delegable OpenID auth info DAO.
    * @return The auth info repository instance.
    */
  @Provides
  def provideAuthInfoRepository(
                                 passwordInfoDAO: DelegableAuthInfoDAO[PasswordInfo],
                                 oauth1InfoDAO: DelegableAuthInfoDAO[OAuth1Info],
                                 oauth2InfoDAO: DelegableAuthInfoDAO[OAuth2Info],
                                 openIDInfoDAO: DelegableAuthInfoDAO[OpenIDInfo]): AuthInfoRepository = {

    new DelegableAuthInfoRepository(passwordInfoDAO, oauth1InfoDAO, oauth2InfoDAO, openIDInfoDAO)
  }

  /**
    * Provides the authenticator service.
    *
    * @param signer The signer implementation.
    * @param crypter The crypter implementation.
    * @param fingerprintGenerator The fingerprint generator.
    * @param sessionCookieBaker The session cookie baker
    * @param configuration The Play configuration.
    * @param clock The clock instance.
    * @return The authenticator service.
    */
  @Provides
  def provideAuthenticatorService(
                                   @Named("authenticator-signer") signer: Signer,
                                   @Named("authenticator-crypter") crypter: Crypter,
                                   fingerprintGenerator: FingerprintGenerator,
                                   sessionCookieBaker: SessionCookieBaker,
                                   configuration: Configuration,
                                   clock: Clock): AuthenticatorService[SessionAuthenticator] = {
    new SessionAuthenticatorService(
      configuration.underlying.as[SessionAuthenticatorSettings]("silhouette.authenticator"),
      fingerprintGenerator,
      new CrypterAuthenticatorEncoder(crypter),
      sessionCookieBaker,
      clock
    )
  }



  /**
    * Provides the authenticator service.
    *
    * @param signer The signer implementation.
    * @param crypter The crypter implementation.
    * @param cookieHeaderEncoding Logic for encoding and decoding `Cookie` and `Set-Cookie` headers.
    * @param fingerprintGenerator The fingerprint generator implementation.
    * @param idGenerator The ID generator implementation.
    * @param configuration The Play configuration.
    * @param clock The clock instance.
    * @return The authenticator service.
    */
  @Provides
  def provideAuthenticatorService(
                                   @Named("authenticator-signer") signer: Signer,
                                   @Named("authenticator-crypter") crypter: Crypter,
                                   cookieHeaderEncoding: CookieHeaderEncoding,
                                   fingerprintGenerator: FingerprintGenerator,
                                   idGenerator: IDGenerator,
                                   configuration: Configuration,
                                   clock: Clock): AuthenticatorService[CookieAuthenticator] = {
    new CookieAuthenticatorService(
      configuration.underlying.as[CookieAuthenticatorSettings]("silhouette.authenticator"),
      None,
      signer,
      cookieHeaderEncoding,
      new CrypterAuthenticatorEncoder(crypter),
      fingerprintGenerator,
      idGenerator,
      clock
    )
  }


  /**
    * Provides the avatar service.
    *
    * @param httpLayer The HTTP layer implementation.
    * @return The avatar service implementation.
    */
  //  @Provides
  //  def provideAvatarService(httpLayer: HTTPLayer): AvatarService = new GravatarService(httpLayer)

  /**
    * Provides the OAuth1 token secret provider.
    *
    * @param signer The cookie signer implementation.
    * @param crypter The crypter implementation.
    * @param configuration The Play configuration.
    * @param clock The clock instance.
    * @return The OAuth1 token secret provider implementation.
    */
  //  @Provides
  //  def provideOAuth1TokenSecretProvider(
  //                                        @Named("oauth1-token-secret-cookie-signer") signer: Signer,
  //                                        @Named("oauth1-token-secret-crypter") crypter: Crypter,
  //                                        configuration: Configuration,
  //                                        clock: Clock): OAuth1TokenSecretProvider = {
  //
  //    val settings = configuration.underlying.as[CookieSecretSettings]("silhouette.oauth1TokenSecretProvider")
  //    new CookieSecretProvider(settings, signer, crypter, clock)
  //  }

  /**
    * Provides the CSRF state item handler.
    *
    * @param idGenerator The ID generator implementation.
    * @param signer The signer implementation.
    * @param configuration The Play configuration.
    * @return The CSRF state item implementation.
    */
  @Provides
  def provideCsrfStateItemHandler(
                                   idGenerator: IDGenerator,
                                   @Named("csrf-state-item-signer") signer: Signer,
                                   configuration: Configuration): CsrfStateItemHandler = {
    new CsrfStateItemHandler(
      configuration.underlying.as[CsrfStateSettings]("silhouette.csrfStateItemHandler"),
      idGenerator,
      signer
    )
  }

  /**
    * Provides the social state handler.
    *
    * @param signer The signer implementation.
    * @return The social state handler implementation.
    */
  @Provides
  def provideSocialStateHandler(
                                 @Named("social-state-signer") signer: Signer,
                                 csrfStateItemHandler: CsrfStateItemHandler): SocialStateHandler = {

    new DefaultSocialStateHandler(Set(csrfStateItemHandler), signer)
  }

  /**
    * Provides the Drops provider.
    *
    * @param httpLayer The HTTP layer implementation.
    * @param stateProvider The OAuth2 state provider implementation.
    * @param configuration The Play configuration.
    * @return The Drops provider.
    */
  @Provides
  def provideDropsProvider(
                            httpLayer: HTTPLayer,
                            //                             stateProvider: OAuth2StateProvider,
                            socialStateHandler : SocialStateHandler,
                            configuration: Configuration): DropsProvider = {

    new DropsProvider(httpLayer, socialStateHandler, configuration.underlying.as[OAuth2Settings]("silhouette.drops"))
  }

//  /**
//    * Provides the Facebook provider.
//    *
//    * @param httpLayer The HTTP layer implementation.
//    * @param stateProvider The OAuth2 state provider implementation.
//    * @param configuration The Play configuration.
//    * @return The Facebook provider.
//    */
  //  @Provides
  //  def provideFacebookProvider(
  //                               httpLayer: HTTPLayer,
  //                               stateProvider: OAuth2StateProvider,
  //                               configuration: Configuration): FacebookProvider = {
  //
  //    new FacebookProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.facebook"))
  //  }

//  /**
//    * Provides the Google provider.
//    *
//    * @param httpLayer The HTTP layer implementation.
//    * @param stateProvider The OAuth2 state provider implementation.
//    * @param configuration The Play configuration.
//    * @return The Google provider.
//    */
  //  @Provides
  //  def provideGoogleProvider(
  //                             httpLayer: HTTPLayer,
  //                             stateProvider: OAuth2StateProvider,
  //                             configuration: Configuration): GoogleProvider = {
  //
  //    new GoogleProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.google"))
  //  }

//  /**
//    * Provides the VK provider.
//    *
//    * @param httpLayer The HTTP layer implementation.
//    * @param stateProvider The OAuth2 state provider implementation.
//    * @param configuration The Play configuration.
//    * @return The VK provider.
//    */
  //  @Provides
  //  def provideVKProvider(
  //                         httpLayer: HTTPLayer,
  //                         stateProvider: OAuth2StateProvider,
  //                         configuration: Configuration): VKProvider = {
  //
  //    new VKProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.vk"))
  //  }

//  /**
//    * Provides the Twitter provider.
//    *
//    * @param httpLayer The HTTP layer implementation.
//    * @param tokenSecretProvider The token secret provider implementation.
//    * @param configuration The Play configuration.
//    * @return The Twitter provider.
//    */
  //  @Provides
  //  def provideTwitterProvider(
  //                              httpLayer: HTTPLayer,
  //                              tokenSecretProvider: OAuth1TokenSecretProvider,
  //                              configuration: Configuration): TwitterProvider = {
  //
  //    val settings = configuration.underlying.as[OAuth1Settings]("silhouette.twitter")
  //    new TwitterProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
  //  }

//  /**
//    * Provides the Xing provider.
//    *
//    * @param httpLayer The HTTP layer implementation.
//    * @param tokenSecretProvider The token secret provider implementation.
//    * @param configuration The Play configuration.
//    * @return The Xing provider.
//    */
  //  @Provides
  //  def provideXingProvider(
  //                           httpLayer: HTTPLayer,
  //                           tokenSecretProvider: OAuth1TokenSecretProvider,
  //                           configuration: Configuration): XingProvider = {
  //
  //    val settings = configuration.underlying.as[OAuth1Settings]("silhouette.xing")
  //    new XingProvider(httpLayer, new PlayOAuth1Service(settings), tokenSecretProvider, settings)
  //  }
}