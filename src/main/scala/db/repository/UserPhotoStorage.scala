package db.repository

import cats.{Apply, Monad}
import doobie.ConnectionIO
import doobie.util.log.LogHandler
import tofu.logging.{Logging, LoggingCompanion}
import tofu.syntax.doobie.log.string._
import cats.tagless.syntax.functorK._
import derevo.derive
import izumi.distage.model.definition.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import tofu.Tries
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.higherKind.derived.representableK
import tofu.logging.derivation.loggingMidTry
import tofu.syntax.monadic._
import doobie.postgres.implicits._

import java.util.UUID

@derive(representableK, loggingMidTry)
trait UserPhotoStorage[F[_]] {
  def getNextPhoto(userId: UUID): F[Option[Int]]
  def upsert(userId: UUID, photoId: Int): F[Unit]
}

object UserPhotoStorage extends LoggingCompanion[UserPhotoStorage] {

  def apply[F[_]: UserPhotoStorage]: UserPhotoStorage[F] = implicitly

  final class Make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]) extends Lifecycle.Of[Identity[*], UserPhotoStorage[F]](
    Lifecycle.pure(make[F, DB](txr))
  )


  def make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]): UserPhotoStorage[F] = {
    val sql = EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
    val tx = txr.trans
    sql.mapK(tx)
  }

  final class Impl(implicit lh: LogHandler) extends UserPhotoStorage[ConnectionIO] {
    def getNextPhoto(userId: UUID): ConnectionIO[Option[Int]] =
      lsql"""SELECT COALESCE(
         |(SELECT photo.id
         | FROM
         | photo,
         | user_photo
         | WHERE
         | user_photo.user_id = $userId
         | AND photo.id > user_photo.last_photo
         | ORDER BY photo.id
         | LIMIT 1),
         |  (
         |  SELECT MIN(id) FROM photo
         |  ))
         | """
        .stripMargin
        .query[Int]
        .option

    override def upsert(userId: UUID, photoId: Int): ConnectionIO[Unit] =
      lsql"""INSERT INTO user_photo (user_id, last_photo) VALUES(
             | $userId,
             | $photoId
             |)
             |ON CONFLICT (user_id) DO UPDATE SET last_photo = $photoId
            """
        .stripMargin
        .update
        .run
        .void
  }
}
