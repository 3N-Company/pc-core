package db.repository

import cats.tagless.syntax.functorK._
import cats.{Apply, Monad}
import db.models.{PhotoSubmission, Submission, UserSubmission}
import derevo.derive
import doobie.ConnectionIO
import doobie.postgres.implicits._
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

import java.util.UUID

@derive(representableK, loggingMidTry)
trait SubmissionStorage[F[_]] {
  def create(photoId: Int, user_id: UUID, metadata: Submission): F[Unit]
  def find(photoId: Int, userID: UUID): F[Option[Submission]]
  def findAllForUser(userId: UUID): F[List[PhotoSubmission]]
  def findAllForPhoto(photoId: Int): F[List[UserSubmission]]
}

object SubmissionStorage extends LoggingCompanion[SubmissionStorage] {

  def apply[F[_]: SubmissionStorage]: SubmissionStorage[F] = implicitly

  final class Make[F[_]: Apply, DB[
      _
  ]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](
      txr: Txr[F, DB]
  ) extends Lifecycle.Of[Identity[*], SubmissionStorage[F]](
        Lifecycle.pure(make[F, DB](txr))
      )

  def make[F[_]: Apply, DB[
      _
  ]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](
      txr: Txr[F, DB]
  ): SubmissionStorage[F] = {
    val sql =
      EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
    val tx  = txr.trans
    sql.mapK(tx)
  }

  final class Impl(implicit lh: LogHandler) extends SubmissionStorage[ConnectionIO] {

    def create(
        photoId: Int,
        userId: UUID,
        metadata: Submission
    ): ConnectionIO[Unit] =
      lsql"""INSERT INTO submission(photo_id, user_id, latitude, longitude, name, photoYear) VALUES(
            |$photoId,
            |$userId,
            |${metadata.position.map(_.latitude)},
            |${metadata.position.map(_.longitude)},
            |${metadata.name},
            |${metadata.photoYear}
            |) ON CONFLICT (photo_id, user_id) DO UPDATE SET name = ${metadata.name}""".stripMargin.update.run.void

    def find(photoId: Int, userId: UUID): ConnectionIO[Option[Submission]] =
      lsql"""SELECT latitude, longitude, name, photoYear
            |FROM submission
            |WHERE photo_id = $photoId AND user_id = $userId
            |""".stripMargin
        .query[Submission]
        .option

    def findAllForPhoto(photoId: Int): ConnectionIO[List[UserSubmission]] =
      lsql"""SELECT user_id, username, u_role, latitude, longitude, name, photoYear
            |FROM submission
            |JOIN users
            |ON user_id = users.id
            |WHERE photo_id = $photoId
            |""".stripMargin
        .query[UserSubmission]
        .to[List]

    def findAllForUser(userId: UUID): ConnectionIO[List[PhotoSubmission]] =
      lsql"""SELECT photo_id, latitude, longitude, name, photoYear
            |FROM submission
            |JOIN photo
            |ON photo_id = photo.id
            |WHERE user_id = $userId
            |""".stripMargin
        .query[PhotoSubmission]
        .to[List]

  }
}
