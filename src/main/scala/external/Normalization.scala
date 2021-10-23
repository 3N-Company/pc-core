package external
import cats.Monad
import common.Config
import db.models.{Submission, UserSubmission}
import db.repository.{MetadataStorage, SubmissionStorage}
import external.models.NormalizationResult
import sttp.client3._
import sttp.client3.circe._
import sttp.model.Uri
import tofu.syntax.feither._
import tofu.syntax.fire._
import tofu.syntax.foption._
import tofu.syntax.monadic._
import tofu.{Fire, Raise}

final class Normalization[F[_]: Monad: MetadataStorage: SubmissionStorage: Raise[*[_], Throwable]: Fire](
    sttpBackend: SttpBackend[F, Any],
    config: Config
) {

  val baseUri: Uri = Uri.apply(config.normalization.host, config.normalization.port)

  def normalize(submissions: List[UserSubmission]): F[NormalizationResult] =
    basicRequest
      .get(baseUri.addPath(config.normalization.path))
      .body(submissions)
      .response(asJson[NormalizationResult])
      .send(sttpBackend)
      .map(_.body)
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
