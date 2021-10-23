package endpoints

import cats.Monad
import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.either._
import cats.syntax.traverse._
import common.Config
import db.models.{PhotoMetadata, Position, Submission, UserSubmission}
import db.repository.{MetadataStorage, PhotoStorage, SubmissionStorage, UserPhotoStorage}
import endpoints.models.PhotoWithSubmissions
import external.{Colorization, Normalization}
import sttp.capabilities
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.{HeaderNames, MediaType, StatusCode}
import sttp.tapir._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import tofu.generate.GenUUID
import tofu.syntax.feither._
import tofu.syntax.foption._
import tofu.syntax.monadic._

import java.nio.file.{Path, StandardOpenOption}

final class PhotoEndpoints[F[
    _
]: Monad: PhotoStorage: SubmissionStorage: UserPhotoStorage: MetadataStorage: Sync: ContextShift: GenUUID: Normalization: Colorization](
    baseEndpoints: BaseEndpoints[F],
    blocker: Blocker,
    config: Config
) extends EndpointsModule[F] {

  val pathPrefix = config.photoFolder + "/"

  val colorizedPrefix = config.photoFolder + "/colorised/"

  val allSumbissions =
    baseEndpoints.secureEndpoint.get
      .in("photo")
      .in(path[Int])
      .in("submissions")
      .out(jsonBody[List[UserSubmission]])
      .serverLogic { case (_, photoId) =>
        SubmissionStorage[F].findAllForPhoto(photoId).map(_.asRight[StatusCode])
      }

  val submitMetadata =
    baseEndpoints.secureEndpoint.post
      .in("photo")
      .in(path[Int])
      .in("submit")
      .in(jsonBody[Submission])
      .serverLogic { case ((user, _), (id, submission)) =>
        SubmissionStorage[F]
          .create(id, user, submission)
          .flatMap(_ => Normalization[F].maybeNormalizeAndSave(id))
          .map(_.asRight[StatusCode])
      }

  val getMetadata =
    endpoint.get
      .in("photo")
      .in(path[Int])
      .in("metadata")
      .out(jsonBody[Submission])
      .errorOut(statusCode)
      .serverLogic { id =>
        MetadataStorage[F].find(id).toRightIn(StatusCode.NotFound)
      }

  val nextPhoto =
    baseEndpoints.secureEndpoint.get
      .in("photo" / "next")
      .out(jsonBody[Option[Int]])
      .serverLogic { case ((user, _), _) =>
        UserPhotoStorage[F]
          .getNextPhoto(user)
          .flatMap { nextPhoto =>
            nextPhoto.traverse(
              UserPhotoStorage[F].upsert(user, _)
            ) >> nextPhoto.pure
          }
          .rightIn[StatusCode]
      }

  val nextPhotoWithSubmissions =
    baseEndpoints.secureEndpoint.get
      .in("photo" / "next-with-submissions")
      .out(jsonBody[Option[PhotoWithSubmissions]])
      .serverLogic { case ((user, _), _) =>
        UserPhotoStorage[F]
          .getNextPhoto(user)
          .semiflatMap { photoId =>
            UserPhotoStorage[F].upsert(user, photoId) >>
              SubmissionStorage[F]
                .findAllForPhoto(photoId)
                .map(PhotoWithSubmissions(photoId, _))
          }
          .rightIn[StatusCode]
      }

  val photosPaged =
    endpoint.get
      .in("photo")
      .in(query[Int]("page").and(query[Int]("size")))
      .out(jsonBody[List[Int]])
      .serverLogic { case (page, size) =>
        PhotoStorage[F]
          .getPaged(page, size)
          .map(_.asRight[Unit])
      }

  val setMetadata =
    baseEndpoints.adminEndpoint.post
      .in("photo")
      .in(path[Int])
      .in("metadata")
      .in(jsonBody[Submission])
      .serverLogic { case (_, (photoId, metadata)) =>
        MetadataStorage[F].upsert(photoId, metadata).map(_.asRight[StatusCode])
      }

  val photosPagedWithMeta =
    endpoint.get
      .in("photo" / "with-meta")
      .in(query[Int]("page").and(query[Int]("size")))
      .out(jsonBody[List[PhotoMetadata]])
      .serverLogic { case (page, size) =>
        PhotoStorage[F]
          .getPagedWithMeta(page, size)
          .map(_.asRight[Unit])
      }

  val allPhotos =
    endpoint.get
      .in("photo" / "all")
      .out(jsonBody[List[Int]])
      .serverLogic { _ =>
        PhotoStorage[F].getAll.map(_.asRight[Unit])
      }

  val allPhotosWithMeta =
    endpoint.get
      .in("photo" / "all-with-meta")
      .out(jsonBody[List[PhotoMetadata]])
      .serverLogic { _ =>
        PhotoStorage[F].getAllWithMeta.map(_.asRight[Unit])
      }

  val getPhoto =
    endpoint.get
      .in("photo")
      .in(path[Int])
      .out(header[Long](HeaderNames.ContentLength))
      .out(header(HeaderNames.ContentType, MediaType.ImageJpeg.toString))
      .out(streamBinaryBody(Fs2Streams[F]))
      .errorOut(statusCode)
      .serverLogic { photoId =>
        PhotoStorage[F]
          .find(photoId)
          .map(_.toRight(StatusCode.NotFound))
          .doubleFlatMap { pathStr =>
            val path = Path.of(pathPrefix ++ pathStr)
            fs2.io.file.size(blocker, path).map { size =>
              val stream = fs2.io.file.readAll(path, blocker, 10 * 1024 * 1024)
              (size, stream).asRight[StatusCode]
            }
          }
      }

  val getPhotoColorized =
    endpoint.get
      .in("photo")
      .in(path[Int])
      .in("colorized")
      .out(header[Long](HeaderNames.ContentLength))
      .out(header(HeaderNames.ContentType, MediaType.ImageJpeg.toString))
      .out(streamBinaryBody(Fs2Streams[F]))
      .errorOut(statusCode)
      .serverLogic { photoId =>
        PhotoStorage[F]
          .find(photoId)
          .map(_.toRight(StatusCode.NotFound))
          .doubleFlatMap { pathStr =>
            val path = Path.of(colorizedPrefix ++ pathStr)
            fs2.io.file.size(blocker, path).map { size =>
              val stream = fs2.io.file.readAll(path, blocker, 10 * 1024 * 1024)
              (size, stream).asRight[StatusCode]
            }
          }
      }

  val uploadPhoto: ServerEndpoint[
    (Option[String], fs2.Stream[F, Byte]),
    StatusCode,
    Int,
    Any with Fs2Streams[F],
    F
  ] =
    baseEndpoints.adminEndpoint.post
      .in("photo")
      .in(streamBinaryBody(Fs2Streams[F]))
      .out(plainBody[Int])
      .serverLogic { case (_, stream) =>
        for {
          name    <- GenUUID[F].randomUUID
          path     = Path.of(pathPrefix ++ name.toString)
          write    = fs2.io.file.writeAll(
                       path,
                       blocker,
                       flags = Seq(StandardOpenOption.CREATE)
                     )
          _       <- stream.through(write).compile.drain
          photoId <- PhotoStorage[F].insert(name.toString)
          _       <- Colorization[F].colorize(name.toString)
        } yield photoId.asRight[StatusCode]
      }

  def getAllPositions =
    baseEndpoints.secureEndpoint.get
      .in("photo" / "submition" / "positions")
      .out(jsonBody[List[Position]])
      .serverLogic { _ =>
        SubmissionStorage[F].findAllPositions.map(_.asRight[StatusCode])
      }

  def all: List[
    ServerEndpoint[_, _, _, Fs2Streams[F] with capabilities.WebSockets, F]
  ] =
    List(
      allSumbissions,
      submitMetadata,
      nextPhoto,
      nextPhotoWithSubmissions,
      allPhotos,
      allPhotosWithMeta,
      photosPaged,
      photosPagedWithMeta,
      getPhoto,
      getPhotoColorized,
      uploadPhoto,
      getMetadata,
      getAllPositions
    )
}
