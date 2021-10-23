package endpoints

import cats.Monad
import cats.syntax.either._
import db.models.{PhotoSubmission, User}
import db.repository.{SubmissionStorage, UserStorage}
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import tofu.syntax.feither._
import tofu.syntax.foption._
import tofu.syntax.monadic._

import java.util.UUID

final class UserEndpoints[F[_]: Monad: UserStorage: SubmissionStorage](
    baseEndpoints: BaseEndpoints[F]
) extends EndpointsModule[F] {

  val users =
    baseEndpoints.adminEndpoint.get
      .in("users")
      .out(jsonBody[List[User]])
      .serverLogic { _ =>
        UserStorage[F].findAll.map(_.asRight[StatusCode])
      }

  val user =
    baseEndpoints.adminEndpoint.get
      .in("users")
      .in(path[UUID])
      .out(jsonBody[User])
      .serverLogic { case (_, user) =>
        UserStorage[F].find(user).toRightIn(StatusCode.NotFound)
      }

  val promote =
    baseEndpoints.adminEndpoint.patch
      .in("users")
      .in(path[UUID])
      .in("promote")
      .out(emptyOutput)
      .serverLogic { case (_, user) =>
        UserStorage[F].promote(user).rightIn[StatusCode]
      }

  val submissions =
    baseEndpoints.adminEndpoint.get
      .in("users")
      .in(path[UUID])
      .in("submissions")
      .out(jsonBody[List[PhotoSubmission]])
      .serverLogic { case (_, user) =>
        SubmissionStorage[F].findAllForUser(user).rightIn[StatusCode]
      }

  def getRating =
    baseEndpoints.secureEndpoint
      .in("users" / "rating")
      .out(jsonBody[Int])
      .serverLogic { case ((user_id, _), _) =>
        SubmissionStorage[F]
          .acceptedSubmissionsForUser(user_id)
          .map(_.getOrElse(0))
          .map(_.asRight[StatusCode])
      }

  def all: List[
    ServerEndpoint[_, _, _, Fs2Streams[F] with capabilities.WebSockets, F]
  ] =
    List(users, user, promote, submissions, getRating)

}
