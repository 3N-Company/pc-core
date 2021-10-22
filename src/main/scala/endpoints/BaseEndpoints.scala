package endpoints

import cats.Monad
import cats.syntax.traverse._
import db.SessionStorage
import db.models.{Role, User}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.PartialServerEndpoint
import tofu.syntax.feither._
import tofu.syntax.monadic._
import distage._

import java.util.UUID

trait BaseEndpoints[F[_]] {
  def secureEndpoint: PartialServerEndpoint[Option[String], UUID, Unit, StatusCode, Unit, Any, F]
  def adminEndpoint: PartialServerEndpoint[Option[String], UUID, Unit, StatusCode, Unit, Any, F]
}

object BaseEndpoints {

  def apply[F[_]: BaseEndpoints]: BaseEndpoints[F] = implicitly

  val authCookie = "JSESSIONID"


  final class Impl[F[_]: SessionStorage: Monad] extends BaseEndpoints[F] {


    def secureEndpoint =
      endpoint
        .in(auth.apiKey(cookie[Option[String]](authCookie)))
        .errorOut(statusCode)
        .serverLogicForCurrent[UUID, F]( c =>
          c.toRight(StatusCode.Unauthorized)
            .map(SessionStorage[F].getUserId)
            .flatTraverse(_.map(_.toRight(StatusCode.NotFound)))
        )


    def adminEndpoint =
      endpoint
        .in(auth.apiKey(cookie[Option[String]](authCookie)))
        .errorOut(statusCode)
        .serverLogicForCurrent[UUID, F](c =>
          c.toRight(StatusCode.Unauthorized)
            .map(SessionStorage[F].getUser)
            .flatTraverse(_.map(_.toRight(StatusCode.NotFound)))
            .flatMapIn{
              case User(id, _, Role.Admin) => Right(id)
              case _ => Left(StatusCode.Forbidden)
            }
        )

  }


}
