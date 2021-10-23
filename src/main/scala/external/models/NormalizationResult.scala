package external.models

import db.models.Position
import derevo.circe.decoder
import derevo.derive
import tofu.logging.derivation.loggable

import java.util.UUID

@derive(decoder, loggable)
case class UserScore(
    id: UUID,
    score: Boolean
)

@derive(decoder, loggable)
case class NormalizationResult(
    mean: Position,
    user: List[UserScore]
)
