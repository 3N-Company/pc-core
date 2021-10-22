package common

import cats.effect.{Blocker, ContextShift, Sync}
import pureconfig._
import pureconfig.module.catseffect.syntax._
import pureconfig.generic.auto._
import distage._

case class Config(
    dbConnectionString: String,
    dbUser: String,
    dbPass: String,
    serverPort: Int,
    photoFolder: String
)

object Config {

  def Module[F[_]: TagK: Sync: ContextShift]: ModuleDef = new ModuleDef {
    make[Config].fromEffect(loadF[F] _)
  }

  def loadF[F[_]: Sync: ContextShift](blocker: Blocker): F[Config] =
    ConfigSource.default.at("settings").loadF[F, Config](blocker)
}
