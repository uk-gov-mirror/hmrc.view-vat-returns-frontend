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

package connectors

import controllers.ControllerBaseSpec
import mocks.MockMetricsService
import uk.gov.hmrc.play.bootstrap.http.HttpClient

class VatReturnsConnectorSpec extends ControllerBaseSpec {

  lazy val connector = new VatReturnsConnector(mock[HttpClient], mockConfig, MockMetricsService)

  "VatReturnsConnector" should {

    "generate the correct returns url with a period key when using vat-returns" in {
      mockConfig.features.enableVatReturnsService(true)
      connector.returnUrl("111", "123") shouldBe "/return-api/vat-returns/returns/vrn/111?period-key=123"
    }

    "generate the correct returns url with a period key when not using vat-returns" in {
      mockConfig.features.enableVatReturnsService(false)
      connector.returnUrl("111", "123") shouldBe "/111/returns/123"
    }

  }

}
