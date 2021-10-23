package common

import cats.effect.{Blocker, ContextShift, Sync}
import distage._
import pureconfig._
import pureconfig.module.catseffect.syntax._
import pureconfig.generic.auto._

case class Normalization(
    host: String,
    port: String,
    path: String,
    threshold: Int
                        )

case class DB(
    connectionString: String,
    user: String,
    pass: String
             )

case class Config(
    db: DB,
    serverPort: Int,
    photoFolder: String,
    normalization: Normalization
)

object Config {

  def Module[F[_]: TagK: Sync: ContextShift]: ModuleDef = new ModuleDef {
    make[Config].fromEffect(loadF[F] _)
  }

  def loadF[F[_]: Sync: ContextShift](blocker: Blocker): F[Config] =
    ConfigSource.default.at("settings").loadF[F, Config](blocker)
}
