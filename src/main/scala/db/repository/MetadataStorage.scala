package db.repository


import cats.{Apply, Monad}
import db.models.{PhotoSubmission, Submission, UserSubmission}
import derevo.derive
import doobie.ConnectionIO
import doobie.util.log.LogHandler
import izumi.distage.model.definition.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import tofu.Tries
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.higherKind.derived.representableK
import tofu.logging.derivation.loggingMidTry
import tofu.logging.{Logging, LoggingCompanion}
import tofu.syntax.doobie.log.string._
import tofu.syntax.monadic._
import doobie.postgres.implicits._
import cats.tagless.syntax.functorK._

import java.util.UUID

@derive(representableK, loggingMidTry)
trait MetadataStorage[F[_]] {
    def upsert(photoId: Int, metadata: Submission): F[Unit]
    def find(photoId: Int): F[Option[Submission]]
}

object MetadataStorage extends LoggingCompanion[MetadataStorage] {

    def apply[F[_]: MetadataStorage]: MetadataStorage[F] = implicitly

    final class Make[F[_]: Apply, DB[
        _
    ]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](
                                                                              txr: Txr[F, DB]
                                                                          ) extends Lifecycle.Of[Identity[*], MetadataStorage[F]](
        Lifecycle.pure(make[F, DB](txr))
    )

    def make[F[_]: Apply, DB[
        _
    ]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](
                                                                              txr: Txr[F, DB]
                                                                          ): MetadataStorage[F] = {
        val sql =
            EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
        val tx = txr.trans
        sql.mapK(tx)
    }

    final class Impl(implicit lh: LogHandler)
        extends MetadataStorage[ConnectionIO] {

        def upsert(
                      photoId: Int,
                      metadata: Submission
                  ): ConnectionIO[Unit] =
            lsql"""INSERT INTO metadata(photo_id, name) VALUES(
                  |$photoId,
                  |${metadata.name}
                  |) ON CONFLICT (photo_id) DO UPDATE SET name = ${metadata.name}""".stripMargin.update.run.void

        def find(photoId: Int): ConnectionIO[Option[Submission]] =
            lsql"""SELECT name
                  |FROM metadata
                  |WHERE photo_id = $photoId
                  |""".stripMargin
                .query[Submission]
                .option

    }
}