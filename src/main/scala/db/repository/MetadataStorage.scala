package db.repository

import cats.tagless.syntax.functorK._
import cats.{Apply, Monad}
import db.models.Submission
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
    val tx  = txr.trans
    sql.mapK(tx)
  }

  final class Impl(implicit lh: LogHandler) extends MetadataStorage[ConnectionIO] {

    def upsert(
        photoId: Int,
        metadata: Submission
    ): ConnectionIO[Unit] =
      lsql"""INSERT INTO metadata(photo_id, latitude, longitude, name, photo_year) VALUES(
                  |$photoId,
                  |${metadata.position.map(_.latitude)},
                  |${metadata.position.map(_.longitude)},
                  |${metadata.name},
                  |${metadata.photoYear}
                  |) ON CONFLICT (photo_id) DO
                  | UPDATE SET
                  |  latitude = ${metadata.position.map(_.latitude)},
                  |  longitude = ${metadata.position.map(_.longitude)},
                  |  name = ${metadata.name},
                  |  photo_year = ${metadata.photoYear}
                  |  """.stripMargin.update.run.void

    def find(photoId: Int): ConnectionIO[Option[Submission]] =
      lsql"""SELECT latitude, longitude, name, photo_year
                  |FROM metadata
                  |WHERE photo_id = $photoId
                  |""".stripMargin
        .query[Submission]
        .option

  }
}
