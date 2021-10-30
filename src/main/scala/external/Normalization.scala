package external
import cats.{Id, Monad}
import common.Config
import db.models.{Submission, UserSubmission}
import db.repository.{MetadataStorage, SubmissionStorage}
import external.models.NormalizationResult
import sttp.client3._
import sttp.client3.circe._
import sttp.model.Uri
import tofu.logging.Logging
import tofu.syntax.feither._
import tofu.syntax.fire._
import tofu.syntax.foption._
import tofu.syntax.monadic._
import tofu.syntax.logging._
import tofu.{Fire, Raise}

final class Normalization[F[_]: Monad: MetadataStorage: SubmissionStorage: Raise[*[_], Throwable]: Fire: Logging.Make](
    sttpBackend: SttpBackend[F, Any],
    config: Config
) {

  implicit val log: Logging[F] = Logging.Make[F].forService[Normalization[F]]

  val baseUri: Uri = Uri.apply(config.normalization.host, config.normalization.port)

  def normalize(submissions: List[UserSubmission]): F[NormalizationResult] =
    basicRequest
      .post(baseUri.addPath(config.normalization.path))
      .body(submissions)
      .response(asJson[NormalizationResult])
      .send(sttpBackend)
      .map(_.body)
      .leftMapF { err =>
        error"${err.getMessage}".map(_ => err)
      }
      .reRaise

  def normalizeAndSave(photoId: Int): F[Unit] =
    for {
      submissions <- SubmissionStorage[F].findAllForPhoto(photoId)
      normalized  <- normalize(submissions)
      _           <- MetadataStorage[F].upsert(photoId, Submission.fromNormalized(normalized)).fireAndForget
      _           <- SubmissionStorage[F].updateSubmissions(photoId, normalized.user).fireAndForget
    } yield ()

  def maybeNormalizeAndSave(photoId: Int): F[Unit] =
    SubmissionStorage[F]
      .countAllForPhoto(photoId)
      .semiflatMap { count =>
        if (count >= config.normalization.threshold)
          normalizeAndSave(photoId)
        else ().pure
      }
      .fireAndForget

}

object Normalization {
  def apply[F[_]: Normalization]: Normalization[F] = implicitly
}
