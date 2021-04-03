package kezek.zeebe.worker.core.util

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.StandardRoute
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import kezek.zeebe.worker.core.exception.ApiException

object HttpUtil {

  def completeThrowable(exception: Throwable): StandardRoute =
    exception match {
      case ex: ApiException =>
        complete(ex.code -> ex.toErrorMessage)
      case ex: Throwable =>
        complete(StatusCodes.ServiceUnavailable -> ApiException.throwableToErrorMessage(ex))
    }
}
