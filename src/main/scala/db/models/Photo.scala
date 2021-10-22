package db.models

import derevo.derive
import tofu.logging.derivation.loggable

@derive(loggable)
case class Photo(path: String)
