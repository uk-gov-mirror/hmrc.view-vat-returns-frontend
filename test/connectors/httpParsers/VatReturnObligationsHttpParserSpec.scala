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

package connectors.httpParsers

import java.time.LocalDate

import connectors.httpParsers.VatReturnObligationsHttpParser.VatReturnObligationsReads
import models.errors._
import models.{VatReturnObligation, VatReturnObligations}
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class VatReturnObligationsHttpParserSpec extends UnitSpec {

  "VatReturnObligationsReads" when {

    "the HTTP response status is OK (200)" should {

      val httpResponse = HttpResponse(Status.OK, responseJson = Some(
        Json.obj(
          "obligations" -> Json.arr(
            Json.obj(
              "start" -> "2017-01-01",
              "end" -> "2017-03-30",
              "due" -> "2017-04-30",
              "status" -> "O",
              "periodKey" -> "#001"
            )
          )
        )
      ))

      val expected = Right(VatReturnObligations(Seq(
        VatReturnObligation(
          start = LocalDate.parse("2017-01-01"),
          end = LocalDate.parse("2017-03-30"),
          due = LocalDate.parse("2017-04-30"),
          status = "O",
          received = None,
          periodKey = "#001"
        )
      )))

      val result = VatReturnObligationsReads.read("", "", httpResponse)

      "return a VatReturnObligations" in {
        result shouldBe expected
      }
    }

    "the HTTP response status is BAD_REQUEST (400) (single error)" should {

      val httpResponse = HttpResponse(Status.BAD_REQUEST, responseJson = Some(
        Json.obj(
          "code" -> "VRN_INVALID",
          "message" -> "Fail!"
        )
      ))

      val expected = Left(BadRequestError(
        code = "VRN_INVALID",
        message = "Fail!"
      ))

      val result = VatReturnObligationsReads.read("", "", httpResponse)

      "return a BadRequestError" in {
        result shouldBe expected
      }
    }

    "the HTTP response status is BAD_REQUEST (400) (multiple errors)" should {

      val httpResponse = HttpResponse(Status.BAD_REQUEST, responseJson = Some(
        Json.obj(
          "code" -> "BAD_REQUEST",
          "message" -> "Fail!",
          "errors" -> Json.arr(
            Json.obj(
              "code" -> "INVALID_DATE_FROM",
              "message" -> "Bad 'from' date"
            ),
            Json.obj(
              "code" -> "INVALID_DATE_TO",
              "message" -> "Bad 'to' date"
            )
          )
        )
      ))

      val expected = Left(MultipleErrors)

      val result = VatReturnObligationsReads.read("", "", httpResponse)

      "return a MultipleErrors" in {
        result shouldBe expected
      }
    }

    "the HTTP response status is BAD_REQUEST (400) (unknown error)" should {

      val httpResponse = HttpResponse(Status.BAD_REQUEST, responseJson = Some(
        Json.obj(
          "foo" -> "RED_CAR",
          "bar" -> "Fail!"
        )
      ))

      val expected = Left(UnknownError)

      val result = VatReturnObligationsReads.read("", "", httpResponse)

      "return an UnknownError" in {
        result shouldBe expected
      }
    }

    "the HTTP response status is 5xx" should {

      val httpResponse = HttpResponse(Status.INTERNAL_SERVER_ERROR)
      val expected = Left(ServerSideError)
      val result = VatReturnObligationsReads.read("", "", httpResponse)

      "return a ServerSideError" in {
        result shouldBe expected
      }
    }

    "the HTTP response status isn't handled" should {

      val httpResponse = HttpResponse(Status.CREATED)
      val expected = Left(UnexpectedStatusError(Status.CREATED))
      val result = VatReturnObligationsReads.read("", "", httpResponse)

      "return an UnexpectedStatusError" in {
        result shouldBe expected
      }
    }
  }
}
