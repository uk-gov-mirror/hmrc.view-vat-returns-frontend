/*
 * Copyright 2019 HM Revenue & Customs
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

import audit.AuditingService
import audit.models.ExtendedAuditModel
import models._
import models.errors.ObligationError
import models.User
import models.viewModels.{ReturnObligationsViewModel, VatReturnsViewModel}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.http.Status
import play.api.mvc.Result
import play.api.test.Helpers._
import services.{DateService, EnrolmentsAuthService, ReturnsService}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ReturnObligationsControllerSpec extends ControllerBaseSpec {

  private trait Test {
    val goodEnrolments: Enrolments = Enrolments(
      Set(
        Enrolment("HMRC-MTD-VAT", Seq(EnrolmentIdentifier("VRN", "999999999")), "Active")
      )
    )

    val exampleObligations: Future[ServiceResponse[VatReturnObligations]] = Right(
      VatReturnObligations(
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
    )
    val serviceCall: Boolean = true
    val openObsServiceCall = false
    val openObligations: Boolean = true
    val authResult: Future[_]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockVatReturnService: ReturnsService = mock[ReturnsService]
    val mockDateService: DateService = mock[DateService]
    val mockAuditService: AuditingService = mock[AuditingService]
    val previousYear: Int = 2017
    val auditingExpected: Boolean = false

    def setup(): Any = {
      (mockDateService.now: () => LocalDate).stubs().returns(LocalDate.parse("2018-05-01"))

      (mockAuthConnector.authorise(_: Predicate, _: Retrieval[_])(_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *)
        .returns(authResult)

      if (serviceCall) {
        (mockVatReturnService.getReturnObligationsForYear(_: User, _: Int, _: Obligation.Status.Value)
        (_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *, *, *)
          .returns(exampleObligations)

        (mockAuditService.extendedAudit(_: ExtendedAuditModel, _: String)(_: HeaderCarrier, _: ExecutionContext))
          .stubs(*, *, *, *)
          .returns({})
      }

      if(openObsServiceCall) {
        (mockVatReturnService.getOpenReturnObligations(_: User)
        (_: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *)
          .returns(exampleObligations)

        (mockAuditService.extendedAudit(_: ExtendedAuditModel, _: String)(_: HeaderCarrier, _: ExecutionContext))
          .stubs(*, *, *, *)
          .returns({})
      }

      if(!openObligations) {
        (mockVatReturnService.getFulfilledObligations(_: LocalDate)
        (_: User, _: HeaderCarrier, _: ExecutionContext))
          .expects(*, *, *, *)
          .returns(Right(VatReturnObligations(Seq.empty)))
      }
    }

    val mockEnrolmentsAuthService: EnrolmentsAuthService = new EnrolmentsAuthService(mockAuthConnector)

    def target: ReturnObligationsController = {
      setup()
      new ReturnObligationsController(
        messages,
        mockEnrolmentsAuthService,
        mockVatReturnService,
        mockDateService,
        mockConfig,
        mockAuditService)
    }
  }

  "Calling the .submittedReturns action" when {

    "A user is logged in and enrolled to HMRC-MTD-VAT" should {

      "return 200" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        private val result = target.submittedReturns(previousYear)(fakeRequest)
        status(result) shouldBe Status.OK
      }

      "return HTML" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        private val result = target.submittedReturns(previousYear)(fakeRequest)
        contentType(result) shouldBe Some("text/html")
      }

      "return charset of utf-8" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        private val result = target.submittedReturns(previousYear)(fakeRequest)
        charset(result) shouldBe Some("utf-8")
      }
    }

    "A user is not authorised" should {

      "return 403 (Forbidden)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(InsufficientEnrolments())
        private val result = target.submittedReturns(previousYear)(fakeRequest)
        status(result) shouldBe Status.FORBIDDEN
      }
    }

    "A user is not authenticated" should {

      "return 401 (Unauthorised)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(MissingBearerToken())
        private val result = target.submittedReturns(previousYear)(fakeRequest)
        status(result) shouldBe Status.UNAUTHORIZED
      }
    }

    "A user enters an invalid search year for their submitted returns" should {

      "return 404 (Not Found)" in new Test {
        override val serviceCall = false
        override val authResult: Future[_] = Future.successful(goodEnrolments)
        private val result = target.submittedReturns(year = 2021)(fakeRequest)
        status(result) shouldBe Status.NOT_FOUND
      }
    }

    "An error occurs upstream" should {

      "return the submitted returns error view" in new Test {
        override val exampleObligations: Future[ServiceResponse[Nothing]] = Left(ObligationError)
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)

        val result: Result = await(target.submittedReturns(previousYear)(fakeRequest))
        val document: Document = Jsoup.parse(bodyOf(result))

        document.select("h1").first().text() shouldBe "Sorry, there is a problem with the service"
      }
    }
  }

  "Calling the .returnDeadlines action" when {

    "A user is logged in and enrolled to HMRC-MTD-VAT" should {

      "return 200" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        override val serviceCall: Boolean = false
        override val openObsServiceCall: Boolean = true
        private val result = target.returnDeadlines()(fakeRequest)
        status(result) shouldBe Status.OK
      }

      "return HTML" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        override val serviceCall: Boolean = false
        override val openObsServiceCall: Boolean = true
        private val result = target.returnDeadlines()(fakeRequest)
        contentType(result) shouldBe Some("text/html")
      }

      "return charset of utf-8" in new Test {
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        override val serviceCall: Boolean = false
        override val openObsServiceCall: Boolean = true
        private val result = target.returnDeadlines()(fakeRequest)
        charset(result) shouldBe Some("utf-8")
      }
    }

    "A user with no open obligations" should {

      "return the no returns view" in new Test {
        override val exampleObligations: Future[ServiceResponse[VatReturnObligations]] = Right(VatReturnObligations(Seq.empty))
        override val serviceCall: Boolean = false
        override val openObsServiceCall: Boolean = true
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)
        override val openObligations: Boolean = false

        val result: Result = await(target.returnDeadlines()(fakeRequest))
        val document: Document = Jsoup.parse(bodyOf(result))

        document.select("article > p:nth-child(3)").text() shouldBe
          "You do not have any returns due right now. Your next deadline will show here on the first day of your next" +
            " accounting period."
      }
    }

    "A user is not authorised" should {

      "return 403 (Forbidden)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(InsufficientEnrolments())
        private val result = target.returnDeadlines()(fakeRequest)
        status(result) shouldBe Status.FORBIDDEN
      }
    }

    "A user is not authenticated" should {

      "return 401 (Unauthorised)" in new Test {
        override val serviceCall = false
        override val authResult: Future[Nothing] = Future.failed(MissingBearerToken())
        private val result = target.returnDeadlines()(fakeRequest)
        status(result) shouldBe Status.UNAUTHORIZED
      }
    }

    "the Obligations API call fails" should {

      "return the technical problem view" in new Test {
        override val exampleObligations: Future[ServiceResponse[Nothing]] = Left(ObligationError)
        override val serviceCall: Boolean = false
        override val openObsServiceCall: Boolean = true
        override val authResult: Future[Enrolments] = Future.successful(goodEnrolments)

        val result: Result = await(target.returnDeadlines()(fakeRequest))
        val document: Document = Jsoup.parse(bodyOf(result))

        document.title shouldBe "There is a problem with the service - VAT reporting through software - GOV.UK"
      }
    }
  }

  private trait HandleReturnObligationsTest {
    val vatServiceResult: Future[ServiceResponse[VatReturnObligations]]
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockVatReturnService: ReturnsService = mock[ReturnsService]
    val mockDateService: DateService = mock[DateService]
    val mockEnrolmentsAuthService: EnrolmentsAuthService = new EnrolmentsAuthService(mockAuthConnector)
    val mockAuditService: AuditingService = mock[AuditingService]
    val testUser: User = User("999999999")
    implicit val hc: HeaderCarrier = HeaderCarrier()

    def setup(): Any = {
      (mockDateService.now: () => LocalDate).stubs().returns(LocalDate.parse("2018-05-01"))

      (mockVatReturnService.getReturnObligationsForYear(_: User, _: Int, _: Obligation.Status.Value)
      (_: HeaderCarrier, _: ExecutionContext))
        .expects(*, *, *, *, *)
        .returns(vatServiceResult)

      (mockAuditService.extendedAudit(_: ExtendedAuditModel, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .stubs(*, *, *, *)
        .returns({})
    }

    def target: ReturnObligationsController = {
      setup()
      new ReturnObligationsController(messages,
        mockEnrolmentsAuthService,
        mockVatReturnService,
        mockDateService,
        mockConfig,
        mockAuditService)
    }
  }

  "Calling the .handleReturnObligations function" when {

    "the vatReturnsService retrieves a valid list of VatReturnObligations" should {

      "return a VatReturnsViewModel with valid obligations" in new HandleReturnObligationsTest {
        override val vatServiceResult: Future[ServiceResponse[VatReturnObligations]] =
          Right(
            VatReturnObligations(Seq(VatReturnObligation(
              LocalDate.parse("2017-01-01"),
              LocalDate.parse("2017-01-01"),
              LocalDate.parse("2017-01-01"),
              "O",
              None,
              "#001"
            )))
          )

        val expectedResult = Right(VatReturnsViewModel(
          Seq(2018, 2017),
          2017,
          Seq(
            ReturnObligationsViewModel(
              LocalDate.parse("2017-01-01"),
              LocalDate.parse("2017-01-01"),
              "#001"
            )
          ),
          hasNonMtdVat = false,
          "999999999"
        ))
        private val result = await(target.getReturnObligations(testUser, 2017, Obligation.Status.All))

        result shouldBe expectedResult
      }
    }

    "the vatReturnsService retrieves an empty list of VatReturnObligations" should {

      "return a VatReturnsViewModel with empty obligations" in new HandleReturnObligationsTest {
        override val vatServiceResult: Future[ServiceResponse[VatReturnObligations]] = Right(VatReturnObligations(Seq.empty))

        val expectedResult = Right(
          VatReturnsViewModel(
            Seq(2018, 2017),
            2017,
            Seq(),
            hasNonMtdVat = false,
            "999999999"
          )
        )
        private val result = await(target.getReturnObligations(testUser, 2017, Obligation.Status.All))

        result shouldBe expectedResult
      }
    }

    "the vatReturnsService retrieves an error" should {

      "return None" in new HandleReturnObligationsTest {
        override val vatServiceResult: Future[ServiceResponse[Nothing]] = Left(ObligationError)
        private val expectedResult = Left(ObligationError)
        private val result = await(target.getReturnObligations(testUser, 2017, Obligation.Status.All))

        result shouldBe expectedResult
      }
    }
  }

  "Calling .isValidSearchYear" when {

    "the year is on the upper search boundary" should {

      "return true" in new Test {
        override val authResult: Future[_] = Future.successful("")
        override def setup(): Any = "" // Prevent the unused mocks causing trouble
        val result: Boolean = target.isValidSearchYear(2018, 2018)
        result shouldBe true
      }
    }

    "the year is above the upper search boundary" should {

      "return false" in new Test {
        override val authResult: Future[_] = Future.successful("")
        override def setup(): Any = "" // Prevent the unused mocks causing trouble
        val result: Boolean = target.isValidSearchYear(2019, 2018)
        result shouldBe false
      }
    }

    "the year is on the lower boundary" should {

      "return true" in new Test {
        override val authResult: Future[_] = Future.successful("")
        override def setup(): Any = "" // Prevent the unused mocks causing trouble
        val result: Boolean = target.isValidSearchYear(2017, 2018)
        result shouldBe true
      }
    }

    "the year is below the lower boundary" should {

      "return false" in new Test {
        override val authResult: Future[_] = Future.successful("")
        override def setup(): Any = "" // Prevent the unused mocks causing trouble
        val result: Boolean = target.isValidSearchYear(2014, 2018)
        result shouldBe false
      }
    }

    "the year is between the upper and lower boundaries" should {

      "return true" in new Test {
        override val authResult: Future[_] = Future.successful("")
        override def setup(): Any = "" // Prevent the unused mocks causing trouble
        val result: Boolean = target.isValidSearchYear(2017, 2018)
        result shouldBe true
      }
    }
  }

  private trait FulfilledObligationsTest {
    val mockAuthConnector: AuthConnector = mock[AuthConnector]
    val mockEnrolmentsAuthService: EnrolmentsAuthService = new EnrolmentsAuthService(mockAuthConnector)
    val mockVatReturnService: ReturnsService = mock[ReturnsService]
    val mockDateService: DateService = mock[DateService]
    val mockAuditService: AuditingService = mock[AuditingService]
    def target: ReturnObligationsController = {
      new ReturnObligationsController(
        messages,
        mockEnrolmentsAuthService,
        mockVatReturnService,
        mockDateService,
        mockConfig,
        mockAuditService)
    }
  }

  "Calling .fulfilledObligationsAction" when {

    "user has no obligations" should {

      val obligationsResult: ServiceResponse[VatReturnObligations] = Right(VatReturnObligations(Seq()))

      "return noUpcomingReturnDeadlines view with no obligation" in new FulfilledObligationsTest {
        val result: Result = target.fulfilledObligationsAction(obligationsResult)
        val document: Document = Jsoup.parse(bodyOf(result))
        document.select("article > p:nth-child(3)").text() shouldBe
          "You do not have any returns due right now. Your next deadline will show here on the first day of your next" +
            " accounting period."
      }
    }

    "user has obligations" should {

      val obligation = VatReturnObligation(
        LocalDate.parse("2017-01-01"),
        LocalDate.parse("2017-04-01"),
        LocalDate.parse("2017-05-11"),
        "F",
        None,
        "#001"
      )
      val obligationsResult: ServiceResponse[VatReturnObligations] = Right(VatReturnObligations(Seq(obligation)))

      "return noUpcomingReturnDeadlines view with the obligation dates"  in new FulfilledObligationsTest {
        val mockReturnsService: ReturnsService = mock[ReturnsService]
        (mockReturnsService.getLastObligation(_: Seq[VatReturnObligation]))
          .expects(*)
          .returns(obligation)
        override val mockVatReturnService: ReturnsService = mockReturnsService
        val result: Result = target.fulfilledObligationsAction(obligationsResult)
        val document: Document = Jsoup.parse(bodyOf(result))

        document.select("p.lede").text() shouldBe
          "We received your return for the period 1 January to 1 April 2017."
      }
    }

    "the service returns an error" should {

      val obligationsResult: ServiceResponse[Nothing] = Left(ObligationError)

      "return the technical problem view" in new FulfilledObligationsTest {
        val result: Result = target.fulfilledObligationsAction(obligationsResult)
        val document: Document = Jsoup.parse(bodyOf(result))
        document.title shouldBe "There is a problem with the service - VAT reporting through software - GOV.UK"
      }
    }
  }
}
