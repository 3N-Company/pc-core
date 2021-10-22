package endpoints

import cats.Monad
import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.either._
import db.models.{Submission, UserSubmission}
import db.repository.{PhotoStorage, SubmissionStorage, UserPhotoStorage}
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{HeaderNames, StatusCode}
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import tofu.generate.GenUUID
import tofu.syntax.feither._
import tofu.syntax.foption._
import tofu.syntax.monadic._
import cats.syntax.traverse._
import common.Config
import endpoints.models.PhotoWithMeta
import sttp.tapir.codec.newtype.codecForNewType


import java.nio.file.{Path, Paths, StandardOpenOption}
import java.util.UUID


final class PhotoEndpoints[F[_]: Monad: PhotoStorage: SubmissionStorage: UserPhotoStorage: Sync: ContextShift: GenUUID]
    (baseEndpoints: BaseEndpoints[F], blocker: Blocker, config: Config) extends EndpointsModule[F] {

    val pathPrefix = config.photoFolder + "/"

    val allSumbissions =
      baseEndpoints
        .secureEndpoint
        .get
        .in("photo")
        .in(path[Int])
        .in("submissions")
        .out(jsonBody[List[UserSubmission]])
        .serverLogic{
          case (_, photoId) => SubmissionStorage[F].findAllForPhoto(photoId).map(_.asRight[StatusCode])
        }

    val submitMetadata =
      baseEndpoints
        .secureEndpoint
        .post
        .in("photo")
        .in(path[Int])
        .in("submit")
        .in(jsonBody[Submission])
        .serverLogic{
          case ((user, _), (id, submission)) =>
            SubmissionStorage[F].create(id, user, submission).map(_.asRight[StatusCode])
        }

    val nextPhoto =
      baseEndpoints
        .secureEndpoint
        .get
        .in("photo" / "next")
        .out(jsonBody[Option[Int]])
        .serverLogic{
          case ((user, _), _) =>
            UserPhotoStorage[F].getNextPhoto(user).flatMap { nextPhoto =>
              nextPhoto.traverse(UserPhotoStorage[F].upsert(user, _)) >> nextPhoto.pure
            }.rightIn[StatusCode]
        }

    val nextPhotoWithMeta =
      baseEndpoints
        .secureEndpoint
        .get
        .in("photo"/ "next-with-meta")
        .out(jsonBody[Option[PhotoWithMeta]])
        .serverLogic{
          case ((user, _), _) =>
            UserPhotoStorage[F].getNextPhoto(user).semiflatMap{ photoId =>
              UserPhotoStorage[F].upsert(user, photoId) >>
                  SubmissionStorage[F].findAllForPhoto(photoId).map(PhotoWithMeta(photoId, _))
            }.rightIn[StatusCode]
        }

    val photosPaged =
      endpoint
        .get
        .in("photo")
        .in(query[Int]("page").and(query[Int]("size")))
        .out(jsonBody[List[Int]])
        .serverLogic{
          case (page, size) =>
            PhotoStorage[F].getPaged(page, size)
            .map(_.asRight[Unit])
        }

    val getPhoto =
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

    val uploadPhoto: ServerEndpoint[(Option[String], fs2.Stream[F, Byte]), StatusCode, Int, Any with Fs2Streams[F], F] =
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
      List(allSumbissions, submitMetadata, nextPhoto, nextPhotoWithMeta, photosPaged, getPhoto, uploadPhoto)
}

