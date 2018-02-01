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

import connectors.httpParsers.ResponseHttpParsers.HttpGetResult
import models.errors.{UnexpectedStatusError, UnknownError}
import models.viewModels.VatReturnViewModel
import models.{User, VatReturn}
import play.api.http.Status
import play.api.test.Helpers._
import services.{EnrolmentsAuthService, ReturnsService, VatApiService}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ReturnsControllerSpec extends ControllerBaseSpec {

  val goodEnrolments: Enrolments = Enrolments(
    Set(
      Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("VATRegNo", "999999999")), "Active")
    )
  )
  val exampleVatReturn: VatReturn = VatReturn(
    LocalDate.parse("2017-01-01"),
    LocalDate.parse("2017-03-31"),
    LocalDate.parse("2017-04-06"),
    LocalDate.parse("2017-04-08"),
    1297,
    5755,
    7052,
    5732,
    1320,
    77656,
    765765,
    55454,
    545645
  )
  val exampleEntityName = Some("Cheapo Clothing")

  private trait Test {
    val serviceCall: Boolean = true
    val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
    val vatReturnResult: Future[HttpGetResult[VatReturn]] = Future.successful(Right(exampleVatReturn))
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockVatReturnService: ReturnsService = mock[ReturnsService]
    val mockVatApiService: VatApiService = mock[VatApiService]

    def setup(): Any = {
      (mockAuthConnector.authorise(_: Predicate, _: Retrieval[_])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returns(authResult)

      if(serviceCall) {
        (mockVatReturnService.getVatReturnDetails(_: User, _: LocalDate, _: LocalDate)(_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *, *, *)
          .returns(vatReturnResult)

        (mockVatApiService.getEntityName(_: User)(_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *)
          .returns(Future.successful(exampleEntityName))
      }
    }

    val mockEnrolmentsAuthService: EnrolmentsAuthService = new EnrolmentsAuthService(mockAuthConnector)

    def target: ReturnsController = {
      setup()
      new ReturnsController(messages, mockEnrolmentsAuthService, mockVatReturnService, mockVatApiService, mockConfig)
    }
  }

  "Calling the .yourVatReturn action" when {

    "a user is logged in and enrolled to HMRC-MTD-VAT" should {

      "return 200" in new Test {
        private val result = target.vatReturnDetails("2017-04-30", "2017-07-31")(fakeRequest)
        status(result) shouldBe Status.OK
      }

      "return HTML" in new Test {
        private val result = target.vatReturnDetails("2017-04-30", "2017-07-31")(fakeRequest)
        contentType(result) shouldBe Some("text/html")
      }

      "return charset of utf-8" in new Test {
        private val result = target.vatReturnDetails("2017-04-30", "2017-07-31")(fakeRequest)
        charset(result) shouldBe Some("utf-8")
      }
    }

    "a user is not authorised" should {

      "return 403 (Forbidden)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(InsufficientEnrolments())
        private val result = target.vatReturnDetails("2017-04-30", "2017-07-31")(fakeRequest)
        status(result) shouldBe Status.FORBIDDEN
      }
    }

    "a user is not authenticated" should {

      "return 401 (Unauthorised)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(MissingBearerToken())
        private val result = target.vatReturnDetails("2017-04-30", "2017-07-31")(fakeRequest)
        status(result) shouldBe Status.UNAUTHORIZED
      }
    }

    "the specified VAT return is not found" should {

      "return 404 (Not Found)" in new Test {
        override val vatReturnResult: Future[HttpGetResult[VatReturn]] =
          Future.successful(Left(UnexpectedStatusError(404)))
        private val result = target.vatReturnDetails("2017-04-30", "2017-07-31")(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "a different error is retrieved" should {

      "return 500 (Internal Server Error)" in new Test {
        override val vatReturnResult: Future[HttpGetResult[VatReturn]] =
          Future.successful(Left(UnknownError))
        private val result = target.vatReturnDetails("2017-04-30", "2017-07-31")(fakeRequest)
        status(result) shouldBe Status.INTERNAL_SERVER_ERROR
      }
    }
  }

  "Calling .constructViewModel" should {

    val mockVatReturnService: ReturnsService = mock[ReturnsService]
    val mockVatApiService: VatApiService = mock[VatApiService]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockEnrolmentsAuthService: EnrolmentsAuthService = new EnrolmentsAuthService(mockAuthConnector)
    val target = new ReturnsController(messages, mockEnrolmentsAuthService, mockVatReturnService, mockVatApiService, mockConfig)

    "populate a VatReturnViewModel" in {

      val expectedViewModel = VatReturnViewModel(
        entityName = exampleEntityName,
        periodFrom = exampleVatReturn.startDate,
        periodTo = exampleVatReturn.endDate,
        dueDate = exampleVatReturn.dueDate,
        dateSubmitted = exampleVatReturn.dateSubmitted,
        boxOne = exampleVatReturn.ukVatDue,
        boxTwo = exampleVatReturn.euVatDue,
        boxThree = exampleVatReturn.totalVatDue,
        boxFour = exampleVatReturn.totalVatReclaimed,
        boxFive = exampleVatReturn.totalOwed,
        boxSix = exampleVatReturn.totalSales,
        boxSeven = exampleVatReturn.totalCosts,
        boxEight = exampleVatReturn.euTotalSales,
        boxNine = exampleVatReturn.euTotalCosts
      )
      val result: VatReturnViewModel = target.constructViewModel(exampleEntityName, exampleVatReturn)
      result shouldBe expectedViewModel
    }
  }
}
