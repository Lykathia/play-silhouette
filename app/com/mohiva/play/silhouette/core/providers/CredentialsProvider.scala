/**
 * Original work: SecureSocial (https://github.com/jaliss/securesocial)
 * Copyright 2013 Jorge Aliss (jaliss at gmail dot com) - twitter: @jaliss
 *
 * Derivative work: Silhouette (https://github.com/mohiva/play-silhouette)
 * Modifications Copyright 2014 Mohiva Organisation (license at mohiva dot com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mohiva.play.silhouette.core.providers

import play.api.data.Form
import play.api.data.Forms._
import com.mohiva.play.silhouette.core._
import play.api.mvc.{ SimpleResult, Results, Result, Request }
import utils.{ GravatarHelper, PasswordHasher }
import play.api.{ Play, Application }
import Play.current
import org.joda.time.DateTime
import com.mohiva.play.silhouette.contrib.User

/**
 * A provider for authenticating credentials.
 */
class CredentialsProvider(application: Application)  {

  def id = CredentialsProvider.ProviderId

  def authMethod = AuthenticationMethod.UserPassword

  val InvalidCredentials = "silhouette.login.invalidCredentials"

  def doAuth[A]()(implicit request: Request[A]): Either[Result, User] = {
    val form = CredentialsProvider.loginForm.bindFromRequest()
    form.fold(
      errors => Left(badRequest(errors, request)),
      credentials => {
        val userId = IdentityID(credentials._1, id)
        val result = for (
          user <- UserService.find(userId).asInstanceOf[Option[User]];
          pinfo <- user.passwordInfo;
          hasher <- Registry.hashers.get(pinfo.hasher) if hasher.matches(pinfo, credentials._2)
        ) yield Right(user)
        result.getOrElse(
          Left(badRequest(CredentialsProvider.loginForm, request, Some(InvalidCredentials))))
      })
  }

  private def badRequest[A](f: Form[(String, String)], request: Request[A], msg: Option[String] = None): SimpleResult = {
    Results.BadRequest("")
  }

  def fillProfile(user: User) = {
    GravatarHelper.avatarFor(user.email.get) match {
      case Some(url) if url != user.avatarURL => user.copy(avatarURL = Some(url))
      case _ => user
    }
  }
}

object CredentialsProvider {
  val ProviderId = "credentials"
  private val Key = "silhouette.userpass.withUserNameSupport"
  private val SendWelcomeEmailKey = "silhouette.userpass.sendWelcomeEmail"
  private val EnableGravatarKey = "silhouette.userpass.enableGravatarSupport"
  private val Hasher = "silhouette.userpass.hasher"
  private val EnableTokenJob = "silhouette.userpass.enableTokenJob"
  private val SignupSkipLogin = "silhouette.userpass.signupSkipLogin"

  val loginForm = Form(
    tuple(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText))

  lazy val withUserNameSupport = current.configuration.getBoolean(Key).getOrElse(false)
  lazy val sendWelcomeEmail = current.configuration.getBoolean(SendWelcomeEmailKey).getOrElse(true)
  lazy val enableGravatar = current.configuration.getBoolean(EnableGravatarKey).getOrElse(true)
  lazy val hasher = current.configuration.getString(Hasher).getOrElse(PasswordHasher.BCryptHasher)
  lazy val enableTokenJob = current.configuration.getBoolean(EnableTokenJob).getOrElse(true)
  lazy val signupSkipLogin = current.configuration.getBoolean(SignupSkipLogin).getOrElse(false)
}

/**
 * A token used for reset password and sign up operations.
 *
 * @param uuid the token id
 * @param email the user email
 * @param creationTime the creation time
 * @param expirationTime the expiration time
 * @param isSignUp a boolean indicating whether the token was created for a sign up action or not
 */
case class Token(uuid: String, email: String, creationTime: DateTime, expirationTime: DateTime, isSignUp: Boolean) {
  def isExpired = expirationTime.isBeforeNow
}


/**
 * The password details
 *
 * @param hasher the id of the hasher used to hash this password
 * @param password the hashed password
 * @param salt the optional salt used when hashing
 */
case class PasswordInfo(hasher: String, password: String, salt: Option[String] = None)