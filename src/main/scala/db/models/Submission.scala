package db.models

import derevo.circe.{decoder, encoder}
import derevo.derive
import sttp.tapir.derevo.schema
import tofu.logging.derivation.loggable


@derive(loggable, encoder, decoder, schema)
case class Submission(name: Option[String])

@derive(loggable, encoder, decoder, schema)
case class UserSubmission(user: User, metadata: Submission)

@derive(loggable, encoder, decoder, schema)
case class PhotoSubmission(photoId: Int, metadata: Submission)

@derive(loggable, encoder, decoder, schema)
case class PhotoMetadata(photoId: Int, metadata: Option[Submission])
