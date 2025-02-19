/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers

import com.google.inject.{Inject, Singleton}
import config.AppConfig
import play.api.mvc._
import services.EnrolmentsAuthService
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthorisationException}
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignOutController @Inject()(enrolmentsAuthService: EnrolmentsAuthService,
                                  mcc: MessagesControllerComponents)
                                 (implicit appConfig: AppConfig,
                                  ec: ExecutionContext) extends FrontendController(mcc) {

  def signOut(authorised: Boolean): Action[AnyContent] = Action.async { implicit request =>
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    if(authorised) {
      enrolmentsAuthService.authorised.retrieve(Retrievals.affinityGroup) {
        case Some(AffinityGroup.Agent) => Future.successful("VATCA")
        case _ => Future.successful("VATC")
      }.map(contactFormIdentifier => Redirect(appConfig.signOutUrl(contactFormIdentifier))).recover {
        case _: AuthorisationException => Redirect(appConfig.unauthorisedSignOutUrl)
      }
    } else {
      Future.successful(Redirect(appConfig.unauthorisedSignOutUrl))
    }
  }

  val timeout: Action[AnyContent] = Action {
    Redirect(appConfig.unauthorisedSignOutUrl)
  }
}
