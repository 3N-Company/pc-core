package external

import cats.Monad
import cats.effect.Sync
import common.Config
import db.models.{Submission, UserSubmission}
import db.repository.{MetadataStorage, SubmissionStorage}
import derevo.circe.encoder
import derevo.derive
import external.models.NormalizationResult
import sttp.client3.circe._
import sttp.client3.{SttpBackend, basicRequest}
import sttp.model.Uri
import tofu.logging.Logging
import tofu.{Fire, Raise}
import tofu.syntax.monadic._

final class Colorization[
    F[_]: Monad: MetadataStorage: SubmissionStorage: Raise[*[_], Throwable]: Fire: Logging.Make: Sync
](
    sttpBackend: SttpBackend[F, Any],
    config: Config
) {

  @derive(encoder)
  case class ColorizeRequest(path: String)

  implicit val log: Logging[F] = Logging.Make[F].forService[Normalization[F]]

  val baseUri: Uri = Uri.apply(config.colorization.host, config.colorization.port)

  def colorize(file: String): F[Unit] =
    basicRequest
      .post(baseUri.addPath(config.colorization.path))
      .body(ColorizeRequest(file))
      .send(sttpBackend)
      .flatMap { x =>
        Sync[F].delay(println(x))
      }
      .void

}

object Colorization {
  def apply[F[_]: Colorization]: Colorization[F] = implicitly
}
