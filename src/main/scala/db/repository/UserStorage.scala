package db.repository

import cats.{Apply, Monad}
import db.models.{Credentials, User}
import derevo.derive
import doobie.postgres.implicits._
import doobie.{ConnectionIO, LogHandler}
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
import cats.tagless.syntax.functorK._

import java.util.UUID

@derive(representableK, loggingMidTry)
trait UserStorage[F[_]] {
  def create(credentials: Credentials): F[UUID]
  def findId(credentials: Credentials): F[Option[UUID]]
  def find(id: UUID): F[Option[User]]
  def findAll: F[List[User]]
  def promote(id: UUID): F[Unit]
}

object UserStorage extends LoggingCompanion[UserStorage] {

  def apply[F[_]: UserStorage]: UserStorage[F] = implicitly

  final class Make[F[_]: Apply, DB[
      _
  ]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](
      txr: Txr[F, DB]
  ) extends Lifecycle.Of[Identity[*], UserStorage[F]](
        Lifecycle.pure(make[F, DB](txr))
      )

  def make[F[_]: Apply, DB[
      _
  ]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](
      txr: Txr[F, DB]
  ): UserStorage[F] = {
    val sql =
      EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
    val tx  = txr.trans
    sql.mapK(tx)
  }

  final class Impl(implicit lh: LogHandler) extends UserStorage[ConnectionIO] {

    def create(credentials: Credentials): ConnectionIO[UUID] =
      lsql"""INSERT INTO users (username, password) VALUES (
            |  ${credentials.username},
            |  crypt(${credentials.password}, gen_salt('bf'))
            |)""".stripMargin.update
        .withUniqueGeneratedKeys[UUID]("id")

    def findId(credentials: Credentials): ConnectionIO[Option[UUID]] =
      lsql"""SELECT id
            |  FROM users
            | WHERE username = ${credentials.username}
            |   AND password = crypt(${credentials.password}, password)""".stripMargin
        .query[UUID]
        .option

    def find(id: UUID): ConnectionIO[Option[User]] =
      lsql"""SELECT id, username, u_role
             |FROM users
             | WHERE id = $id
            """.stripMargin
        .query[User]
        .option

    def findAll: ConnectionIO[List[User]] =
      lsql"""SELECT id, username, u_role
             |FROM users
            """.stripMargin
        .query[User]
        .to[List]

    def promote(id: UUID): ConnectionIO[Unit] =
      lsql"""UPDATE users
             | SET u_role = 'admin'
             | WHERE id = $id
            """.stripMargin.update.run.void
  }

}
