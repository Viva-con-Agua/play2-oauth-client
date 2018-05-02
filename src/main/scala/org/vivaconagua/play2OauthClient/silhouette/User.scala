package org.vivaconagua.play2OauthClient.silhouette

import java.util.UUID
import com.mohiva.play.silhouette.api.Identity

case class User(uuid: UUID) extends Identity
