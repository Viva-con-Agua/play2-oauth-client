package org.vivaconagua.play2OauthClient.silhouette

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.SessionAuthenticator

trait SessionEnv extends Env {
  type I = User
  type A = SessionAuthenticator
}