/*
 * Copyright 2018 HM Revenue & Customs
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

package services

import javax.inject.{Inject, Singleton}
import connectors.VatSubscriptionConnector
import models.customer.CustomerDetail
import models.{CustomerInformation, User}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionService @Inject()(connector: VatSubscriptionConnector) {

  def getUserDetails(user: User)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CustomerDetail]] = {
    connector.getCustomerInfo(user.vrn).map {

      case Right(CustomerInformation(None, None, None, None, _, _)) => None

      case Right(model) =>

        val entityName: String = (model.firstName, model.lastName, model.organisationName, model.tradingName) match {
          case (_, _, _, Some(tradingName)) => tradingName
          case (_, _, Some(organisationName), _) => organisationName
          case (Some(firstName), Some(lastName), _, _) => s"$firstName $lastName"
        }

        val isPartialMigration: Boolean = model.isPartialMigration.contains(true)

        Some(CustomerDetail(entityName, model.hasFlatRateScheme, isPartialMigration))

      case Left(_) => None
    }
  }
}
