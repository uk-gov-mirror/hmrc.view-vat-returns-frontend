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

import connectors.VatApiConnector
import connectors.httpParsers.CustomerInfoHttpParser.HttpGetResult
import controllers.ControllerBaseSpec
import models.errors.BadRequestError
import models.{CustomerInformation, User}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.{ExecutionContext, Future}

class CustomerInfoServiceSpec extends ControllerBaseSpec {

  private trait Test {
    val exampleCustomerInfo: CustomerInformation = CustomerInformation(
      Some("Cheapo Clothing Ltd"),
      Some("John"),
      Some("Smith"),
      Some("Cheapo Clothing")
    )
    val mockConnector: VatApiConnector = mock[VatApiConnector]
    val service: CustomerInfoService = new CustomerInfoService(mockConnector)
    implicit val hc: HeaderCarrier = HeaderCarrier()
  }

  "Calling .getCustomerInfo" when {

    "the connector returns a successful response" should {

      "return a user's information" in new Test {
        (mockConnector.getCustomerInfo(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *)
          .returns(Future.successful(Right(exampleCustomerInfo)))

        lazy val result: HttpGetResult[CustomerInformation] = await(service.getEntityName(User("999999999")))

        result shouldBe Right(exampleCustomerInfo)
      }
    }

    "the connector returns an error" should {

      "return an error" in new Test {
        (mockConnector.getCustomerInfo(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *)
          .returns(Future.successful(Left(BadRequestError("", ""))))

        val result: HttpGetResult[CustomerInformation] = await(service.getEntityName(User("999999999")))

        result shouldBe Left(BadRequestError("", ""))
      }
    }
  }
}
