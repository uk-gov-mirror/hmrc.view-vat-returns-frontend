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

package connectors

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import javax.inject.{Inject, Singleton}

import config.AppConfig
import connectors.httpParsers.ResponseHttpParsers.HttpGetResult
import models.VatReturnObligation.Status
import models.{VatReturn, VatReturnObligations}
import play.api.Logger
import services.MetricsService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class VatApiConnector @Inject()(http: HttpClient,
                                appConfig: AppConfig,
                                metrics: MetricsService) {

  private[connectors] def obligationsUrl(vrn: String): String = s"${appConfig.vatApiBaseUrl}/$vrn/obligations"

  private[connectors] def returnUrl(vrn: String, periodKey: String) = s"${appConfig.vatApiBaseUrl}/$vrn/returns/${URLEncoder.encode(periodKey, UTF_8.name())}"

  def getVatReturnDetails(vrn: String, periodKey: String)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpGetResult[VatReturn]] = {

    import connectors.httpParsers.VatReturnHttpParser.VatReturnReads

    val timer = metrics.getVatReturnTimer.time()

    http.GET(returnUrl(vrn, periodKey)).map {
      case nineBox@Right(_) =>
        timer.stop()
        nineBox
      case httpError@Left(error) =>
        metrics.getVatReturnCallFailureCounter.inc()
        Logger.info("VatApiConnector received error: " + error.message)
        httpError
    }
  }

  def getVatReturnObligations(vrn: String, from: LocalDate, to: LocalDate, status: Status.Value)
                             (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpGetResult[VatReturnObligations]] = {

    import connectors.httpParsers.VatReturnObligationsHttpParser.VatReturnObligationsReads

    val timer = metrics.getObligationsTimer.time()

    http.GET(obligationsUrl(vrn), Seq("from" -> from.toString, "to" -> to.toString, "status" -> status.toString)).map {
      case vatReturns@Right(_) =>
        timer.stop()
        vatReturns
      case httpError@Left(error) =>
        metrics.getObligationsCallFailureCounter.inc()
        Logger.info("VatApiConnector received error: " + error.message)
        httpError
    }
  }
}
