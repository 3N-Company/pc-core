package endpoints

import cats.Monad
import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.either._
import db.models.UserSubmission
import db.{PhotoStorage, SubmissionStorage}
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import tofu.generate.GenUUID
import tofu.syntax.feither._
import tofu.syntax.monadic._

import java.nio.file.{Path, Paths, StandardOpenOption}


final class PhotoEndpoints[F[_]: Monad: PhotoStorage: SubmissionStorage: Sync: ContextShift: GenUUID]
    (baseEndpoints: BaseEndpoints[F], blocker: Blocker) extends EndpointsModule[F] {

    val pathPrefix = "./photo/"

    def allSumbissions =
      baseEndpoints
        .adminEndpoint
        .get
        .in("photo")
        .in(path[Int])
        .in("submissions")
        .out(jsonBody[List[UserSubmission]])
        .serverLogic{
          case (_, photoId) => SubmissionStorage[F].findAllForPhoto(photoId).map(_.asRight[StatusCode])
        }

    def getPhoto =
      endpoint
        .get
        .in("photo")
        .in(path[Int])
        .out(header[Long](HeaderNames.ContentLength))
        .out(streamBinaryBody(Fs2Streams[F]))
        .errorOut(statusCode)
        .serverLogic{ photoId =>
            PhotoStorage[F].find(photoId)
              .map(_.toRight(StatusCode.NotFound))
              .doubleFlatMap{ pathStr =>
                val path = Path.of(pathStr)
                fs2.io.file.size(blocker, path).map{ size =>
                  val stream =  fs2.io.file.readAll(path, blocker, 10 * 1024 * 1024)
                  (size, stream).asRight[StatusCode]
                }
              }
        }

    def uploadPhoto: ServerEndpoint[(Option[String], fs2.Stream[F, Byte]), StatusCode, Int, Any with Fs2Streams[F], F] =
      baseEndpoints
        .adminEndpoint
        .post
        .in("photo")
        .in(streamBinaryBody(Fs2Streams[F]))
        .out(plainBody[Int])
        .serverLogic{
          case (_, stream) =>
            for {
              name <- GenUUID[F].randomUUID
              path = Path.of(pathPrefix ++ name.toString)
              write = fs2.io.file.writeAll(path, blocker, flags = Seq(StandardOpenOption.CREATE))
              _ <- stream.through(write).compile.drain
              photoId <- PhotoStorage[F].insert(path.toString)
            } yield photoId.asRight[StatusCode]
        }

    def all: List[ServerEndpoint[_, _, _, Fs2Streams[F] with capabilities.WebSockets, F]] =
      List(allSumbissions, getPhoto, uploadPhoto)
}

