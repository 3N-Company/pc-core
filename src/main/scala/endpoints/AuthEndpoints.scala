package endpoints

import cats.Monad
import db.models.Credentials
import db.repository.{SessionStorage, UserStorage}
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.EndpointIO.Header
import sttp.tapir.EndpointInput.WWWAuthenticate
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.ServerEndpoint
import tofu.syntax.monadic._
import tofu.syntax.foption._
import tofu.syntax.feither._
import cats.syntax.traverse._
import cats.syntax.either._

final class AuthEndpoints[F[_]: Monad: UserStorage: SessionStorage](
    baseEndpoints: BaseEndpoints[F]
) extends EndpointsModule[F] {

  val login: ServerEndpoint[
    UsernamePassword,
    StatusCode,
    CookieValueWithMeta,
    Any,
    F
  ] =
    endpoint.post
      .in("login")
      .in(
        auth.basic[UsernamePassword](
          WWWAuthenticate.basic(WWWAuthenticate.DefaultRealm)
        )
      )
      .out(setCookie(BaseEndpoints.authCookie))
      .errorOut(statusCode)
      .serverLogic { credentials =>
        credentials.password
          .map(Credentials(credentials.username, _))
          .flatTraverse(UserStorage[F].findId)
          .map(_.toRight(StatusCode.NotFound))
          .doubleFlatMap { uuid =>
            SessionStorage[F]
              .createSessionCookie(uuid)
              .map(_.toRight(StatusCode.InternalServerError))
              .mapIn(x => CookieValueWithMeta.unsafeApply(value = x))
          }
      }

  val logout =
    baseEndpoints.secureEndpoint
      .in("logout")
      .out(emptyOutput)
      .serverLogic { case ((_, cookie), _) =>
        SessionStorage[F].deleteSession(cookie).rightIn[StatusCode]
      }

  val logoutAll =
    baseEndpoints.secureEndpoint
      .in("logoutAll")
      .out(emptyOutput)
      .serverLogic { case ((user, _), _) =>
        SessionStorage[F].deleteAllSessions(user).rightIn[StatusCode]
      }

  val register =
    endpoint.post
      .in("register")
      .in(jsonBody[Credentials])
      .out(emptyOutput)
      .serverLogic { credentials =>
        UserStorage[F].create(credentials).void.rightIn[Unit]
      }

  override def all: List[
    ServerEndpoint[_, _, _, Fs2Streams[F] with capabilities.WebSockets, F]
  ] =
    List(login, logout, logoutAll, register)
}
