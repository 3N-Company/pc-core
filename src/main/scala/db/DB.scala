package db
import cats.Monad
import cats.effect.{ContextShift, Effect}
import common.Config
import db.repository._
import distage._
import doobie.util.transactor.Transactor
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.{EmbeddableLogHandler, LogHandlerF}
import tofu.doobie.transactor.Txr
import tofu.lift.UnliftIO
import tofu.logging.Logging
import tofu.syntax.doobie.log.handler._
import tofu.{Delay, Errors, Tries}

object DB {
  def Module[F[_]: TagK: Effect: ContextShift, DB[
      _
  ]: TagK: Delay: Monad: ContextShift: UnliftIO: Tries: LiftConnectionIO]: ModuleDef = new ModuleDef {
    make[Transactor[F]].from { config: Config =>
      Transactor.fromDriverManager[F](
        driver = "org.postgresql.Driver",
        url = config.dbConnectionString,
        user = config.dbUser,
        pass = config.dbPass
      )
    }

    make[Tries[DB]].from(Errors[DB, Throwable])
    make[Monad[DB]].from(Monad[DB])
    make[Logging.Make[DB]].from(Logging.Make.plain[DB])
    make[LiftConnectionIO[DB]].from(LiftConnectionIO[DB])

    make[Txr.Continuational[F]].from { transactor: Transactor[F] =>
      Txr.continuational(transactor)
    }

    make[EmbeddableLogHandler[DB]].from { implicit l: Logging.Make[DB] =>
      EmbeddableLogHandler.sync[DB](LogHandlerF.loggable[DB](Logging.Debug))
    }

    make[SessionSql[DB]].fromResource[SessionSql.Make[DB]]
    make[SessionStorage[F]].from(SessionStorage.make[F, DB] _)
    make[PhotoStorage[F]].fromResource[PhotoStorage.Make[F, DB]]
    make[UserStorage[F]].fromResource[UserStorage.Make[F, DB]]
    make[SubmissionStorage[F]].fromResource[SubmissionStorage.Make[F, DB]]
    make[UserPhotoStorage[F]].fromResource[UserPhotoStorage.Make[F, DB]]
    make[MetadataStorage[F]].fromResource[MetadataStorage.Make[F, DB]]

    make[Migrator[F]].from[Migrator[F]]

  }

}
