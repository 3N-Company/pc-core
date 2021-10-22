package db

import cats.{Apply, Monad}
import db.models.{PhotoSubmission, Submission, UserSubmission}
import derevo.derive
import doobie.ConnectionIO
import doobie.util.log.LogHandler
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.doobie.transactor.Txr
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, LoggingCompanion}
import tofu.logging.derivation.loggingMidTry
import tofu.syntax.monadic._
import tofu.syntax.doobie.log.string._
import cats.tagless.syntax.functorK._
import doobie.postgres._
import doobie.postgres.implicits._
import izumi.distage.model.definition.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import tofu.Tries

import java.util.UUID

@derive(representableK, loggingMidTry)
trait SubmissionStorage[F[_]] {
  def init: F[Unit]
  def create(photoId: Int, user_id: UUID, metadata: Submission): F[Unit]
  def find(photoId: Int, userID: UUID): F[Option[Submission]]
  def findAllForUser(userId: UUID): F[List[PhotoSubmission]]
  def findAllForPhoto(photoId: Int): F[List[UserSubmission]]
}

object SubmissionStorage extends LoggingCompanion[SubmissionStorage] {

  def apply[F[_]: SubmissionStorage]: SubmissionStorage[F] = implicitly



  final class Make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]) extends Lifecycle.Of[Identity[*], SubmissionStorage[F]](
    Lifecycle.pure(make[F, DB](txr))
  )

  def make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]): SubmissionStorage[F] = {
    val sql = EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
    val tx = txr.trans
    sql.mapK(tx)
  }

  final class Impl(implicit lh: LogHandler) extends SubmissionStorage[ConnectionIO] {
    def init: ConnectionIO[Unit] =
      lsql"""CREATE TABLE IF NOT EXISTS submission
            |(
            | photo_id integer NOT NULL,
            | user_id  uuid NOT NULL,
            | name     text NULL,
            | CONSTRAINT PK_53 PRIMARY KEY ( photo_id, user_id ),
            | CONSTRAINT FK_28 FOREIGN KEY ( photo_id ) REFERENCES photo ( "id" ),
            | CONSTRAINT FK_31 FOREIGN KEY ( user_id ) REFERENCES users ( "id" )
            |);
            |
            |CREATE INDEX IF NOT EXISTS fkIdx_30 ON submission
            |(
            | photo_id
            |);
            |
            |CREATE INDEX IF NOT EXISTS fkIdx_33 ON submission
            |(
            | user_id
            |);"""
        .stripMargin
        .update
        .run
        .void

    def create(photoId: Int, userId: UUID, metadata: Submission): ConnectionIO[Unit] =
      lsql"""INSERT INTO submission(photo_id, user_id, name) VALUES(
            |$photoId,
            |$userId,
            |${metadata.name}
            |) ON CONFLICT (photo_id, user_id) DO UPDATE SET name = ${metadata.name}"""
        .stripMargin
        .update
        .run
        .void

    def find(photoId: Int, userId: UUID): ConnectionIO[Option[Submission]] =
      lsql"""SELECT name
            |FROM submission
            |WHERE photo_id = $photoId AND user_id = $userId
            |"""
        .stripMargin
        .query[Submission]
        .option

    def findAllForPhoto(photoId: Int): ConnectionIO[List[UserSubmission]] =
      lsql"""SELECT user_id, username, u_role, name
            |FROM submission
            |JOIN users
            |ON user_id = users.id
            |WHERE photo_id = $photoId
            |"""
        .stripMargin
        .query[UserSubmission]
        .to[List]

    def findAllForUser(userId: UUID): ConnectionIO[List[PhotoSubmission]] =
      lsql"""SELECT photo_id, name
            |FROM submission
            |JOIN photo
            |ON photo_id = photo.id
            |WHERE user_id = $userId
            |"""
        .stripMargin
        .query[PhotoSubmission]
        .to[List]


  }
}
