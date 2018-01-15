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

package controllers

import java.time.LocalDate

import models.{User, VatReturnObligation, VatReturnObligations}
import play.api.http.Status
import play.api.test.Helpers._
import services.{EnrolmentsAuthService, ReturnsService}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ReturnObligationsControllerSpec extends ControllerBaseSpec {

  private trait Test {
    val exampleObligations: VatReturnObligations = VatReturnObligations(
      Seq(
        VatReturnObligation(
          LocalDate.parse("2017-01-01"),
          LocalDate.parse("2017-12-31"),
          LocalDate.parse("2018-01-31"),
          "O",
          None,
          "#001"
        )
      )
    )

    val serviceCall: Boolean = true
    val authResult: Future[_]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockVatReturnService: ReturnsService = mock[ReturnsService]

    def setup(): Any = {
      (mockAuthConnector.authorise(_: Predicate, _: Retrieval[_])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returns(authResult)

      if (serviceCall) {
        (mockVatReturnService.getAllReturns(_: User, _: LocalDate)(_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *, *)
          .returns(Future.successful(Right(exampleObligations)))
      }
    }

    val mockEnrolmentsAuthService: EnrolmentsAuthService = new EnrolmentsAuthService(mockAuthConnector)

    def target: ReturnObligationsController = {
      setup()
      new ReturnObligationsController(messages, mockEnrolmentsAuthService, mockVatReturnService, mockConfig)
    }
  }

  "Calling the .vatReturnsList action" when {

    "A user is logged in and enrolled to HMRC-MTD-VAT" should {

      val goodEnrolments: Enrolments = Enrolments(
        Set(
          Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("", "999999999")), "Active")
        )
      )

      "return 200" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        private val result = target.vatReturnsList()(fakeRequest)
        status(result) shouldBe Status.OK
      }

      "return HTML" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        private val result = target.vatReturnsList()(fakeRequest)
        contentType(result) shouldBe Some("text/html")
      }

      "return charset of utf-8" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        private val result = target.vatReturnsList()(fakeRequest)
        charset(result) shouldBe Some("utf-8")
      }
    }

    "A user is not authorised" should {

      "return 403 (Forbidden)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(InsufficientEnrolments())
        private val result = target.vatReturnsList()(fakeRequest)
        status(result) shouldBe Status.FORBIDDEN
      }
    }

    "A user is not authenticated" should {

      "return 401 (Unauthorised)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(MissingBearerToken())
        private val result = target.vatReturnsList()(fakeRequest)
        status(result) shouldBe Status.UNAUTHORIZED
      }
    }
  }
}
