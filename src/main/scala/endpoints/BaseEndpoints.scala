package endpoints

import cats.Monad
import cats.syntax.traverse._
import db.models.{Role, User}
import db.repository.SessionStorage
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.PartialServerEndpoint
import tofu.syntax.feither._
import tofu.syntax.monadic._
import tofu.syntax.foption._
import tofu.syntax.feither

import java.util.UUID

trait BaseEndpoints[F[_]] {
  def secureEndpoint: PartialServerEndpoint[Option[
    String
  ], (UUID, String), Unit, StatusCode, Unit, Any, F]
  def adminEndpoint: PartialServerEndpoint[Option[
    String
  ], UUID, Unit, StatusCode, Unit, Any, F]
}

object BaseEndpoints {

  def apply[F[_]: BaseEndpoints]: BaseEndpoints[F] = implicitly

  val authCookie = "JSESSIONID"

  final class Impl[F[_]: SessionStorage: Monad] extends BaseEndpoints[F] {

    val secureEndpoint =
      endpoint
        .in(auth.apiKey(cookie[Option[String]](authCookie)))
        .errorOut(statusCode)
        .serverLogicForCurrent[(UUID, String), F](c =>
          c.toRight(StatusCode.Unauthorized)
            .map(cookie => SessionStorage[F].getUserId(cookie).mapIn((_, cookie)))
            .flatTraverse(_.map(_.toRight(StatusCode.Unauthorized)))
        )

    val adminEndpoint =
      endpoint
        .in(auth.apiKey(cookie[Option[String]](authCookie)))
        .errorOut(statusCode)
        .serverLogicForCurrent[UUID, F](c =>
          c.toRight(StatusCode.Unauthorized)
            .map(SessionStorage[F].getUser)
            .flatTraverse(_.map(_.toRight(StatusCode.Unauthorized)))
            .flatMapIn {
              case User(id, _, Role.Admin) => Right(id)
              case _                       => Left(StatusCode.Forbidden)
            }
        )

  }

}
