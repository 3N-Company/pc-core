package endpoints.models

import derevo.circe.{decoder, encoder}
import derevo.derive
import sttp.tapir.derevo.schema

@derive(encoder, decoder, schema)
case class SetCookie (
    key: String,
    value: String
                     )
