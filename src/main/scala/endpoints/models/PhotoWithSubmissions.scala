package endpoints.models

import db.models.UserSubmission
import derevo.circe.{decoder, encoder}
import derevo.derive
import sttp.tapir.derevo.schema

@derive(encoder, decoder, schema)
case class PhotoWithSubmissions(id: Int, userSubmissions: List[UserSubmission])
