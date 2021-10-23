package endpoints

import cats.Monad
import cats.syntax.traverse._
import db.models.Credentials
import db.repository.{SessionStorage, UserStorage}
import endpoints.models.SetCookie
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.EndpointInput.WWWAuthenticate
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.model.UsernamePassword
import sttp.tapir.server.ServerEndpoint
import tofu.syntax.feither._
import tofu.syntax.foption._
import tofu.syntax.monadic._

final class AuthEndpoints[F[_]: Monad: UserStorage: SessionStorage](
    baseEndpoints: BaseEndpoints[F]
) extends EndpointsModule[F] {

  val login =
    endpoint.post
      .in("login")
      .in(
        auth.basic[UsernamePassword](
          WWWAuthenticate.basic(WWWAuthenticate.DefaultRealm)
        )
      )
      //workaround - frontend bug
         .out(jsonBody[SetCookie])
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
              .mapIn(x => (SetCookie(BaseEndpoints.authCookie, x), CookieValueWithMeta.unsafeApply(value = x)))
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
      .errorOut(statusCode)
      .serverLogic { credentials =>
        UserStorage[F]
          .create(credentials)
          .toRightIn(StatusCode.Conflict)
            .mapIn(_ => ())
      }

  override def all: List[
    ServerEndpoint[_, _, _, Fs2Streams[F] with capabilities.WebSockets, F]
  ] =
    List(login, logout, logoutAll, register)
}
