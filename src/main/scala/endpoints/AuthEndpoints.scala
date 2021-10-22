package endpoints

import cats.Monad
import db.models.Credentials
import db.{SessionStorage, UserStorage}
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import tofu.syntax.monadic._
import tofu.syntax.foption._
import tofu.syntax.feither._

final class AuthEndpoints[F[_]: Monad: UserStorage: SessionStorage](baseEndpoints: BaseEndpoints[F]) extends EndpointsModule[F] {

  def login: ServerEndpoint[Credentials, StatusCode, CookieValueWithMeta, Any, F] =
    endpoint
      .post
      .in("login")
      .in(jsonBody[Credentials])
      .out(setCookie(BaseEndpoints.authCookie))
      .errorOut(statusCode)
      .serverLogic{ credentials =>
        UserStorage[F]
          .find(credentials)
          .map(_.toRight(StatusCode.NotFound))
          .doubleFlatMap{ uuid =>
            SessionStorage[F].createSessionCookie(uuid)
              .map(_.toRight(StatusCode.InternalServerError))
              .mapIn(x => CookieValueWithMeta.unsafeApply(value = x))
          }
      }

  override def all: List[ServerEndpoint[_, _, _, Fs2Streams[F] with capabilities.WebSockets, F]] =
    List(login)
}
