package db.models

import derevo.circe.{decoder, encoder}
import derevo.derive
import external.models.NormalizationResult
import sttp.tapir.derevo.schema
import tofu.logging.derivation.loggable

@derive(loggable, encoder, decoder, schema)
case class Position(latitude: String, longitude: String)

@derive(loggable, encoder, decoder, schema)
case class Submission(position: Option[Position] = None, name: Option[String] = None, photoYear: Option[Int] = None)

object Submission {
  def fromNormalized(normalizationResult: NormalizationResult): Submission =
      Submission(position = Some(normalizationResult.mean))
}

@derive(loggable, encoder, decoder, schema)
case class UserSubmission(user: User, metadata: Submission)

@derive(loggable, encoder, decoder, schema)
case class PhotoSubmission(photoId: Int, metadata: Submission)

@derive(loggable, encoder, decoder, schema)
case class PhotoMetadata(photoId: Int, metadata: Option[Submission])
