package db

import cats.Monad
import db.models.User
import derevo.derive
import doobie.ConnectionIO
import doobie.util.log.LogHandler
import tofu.logging.{Logging, LoggingCompanion}
import tofu.syntax.monadic._
import tofu.syntax.doobie.log.string._
import doobie.postgres._
import doobie.postgres.implicits._
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.higherKind.derived.representableK
import tofu.logging.derivation.loggingMidTry
import distage._
import izumi.fundamentals.platform.functional.Identity
import tofu.Tries

import java.util.UUID

@derive(representableK, loggingMidTry)
trait SessionSql[F[_]] {
  def init: F[Unit]
  def createSession(userId: UUID): F[UUID]
  def createCookie(sessionID: UUID): F[Option[String]]
  def getUserId(cookie: String): F[Option[UUID]]
  def getUser(cookie: String): F[Option[User]]
  def deleteSession(cookie: String): F[Unit]
}

object SessionSql extends LoggingCompanion[SessionSql] {

  def apply[F[_]: SessionSql]: SessionSql[F] = implicitly

  final class Make[DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries]  extends Lifecycle.Of[Identity[*], SessionSql[DB]] (
    Lifecycle.pure(make[DB])
  )

  def make[DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries]: SessionSql[DB] = {
    EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
  }


  final class Impl(implicit lh: LogHandler) extends SessionSql[ConnectionIO] {
    def init: ConnectionIO[Unit] =
      lsql"""CREATE TABLE IF NOT EXISTS sessions (
            |  id uuid NOT NULL DEFAULT gen_random_uuid(),
            |  user_id uuid NOT NULL,
            |  key text NOT NULL DEFAULT gen_salt('md5'),
            |  CONSTRAINT PK_15 PRIMARY KEY ("id"),
            |  CONSTRAINT FK_40 FOREIGN KEY ( user_id ) REFERENCES users ("id")
            |);
            |
            |CREATE INDEX IF NOT EXISTS sessions_cookie_idx ON sessions(
            | (id || '-' || encode(hmac(id::text, "key", 'sha256'), 'hex'))
            |);
            |"""
        .stripMargin
        .update
        .run
        .void

    def createSession(userId: UUID): ConnectionIO[UUID] =
      lsql"""INSERT INTO sessions (user_id) VALUES ($userId)"""
        .update
        .withUniqueGeneratedKeys[UUID]("id")

    def createCookie(sessionId: UUID): ConnectionIO[Option[String]] =
      lsql"""SELECT (
             | id || '-' || encode(hmac(id::text, key, 'sha256'), 'hex')
             |) FROM sessions WHERE id = $sessionId
            """
        .stripMargin
        .query[String]
        .option


    def getUserId(cookie: String): ConnectionIO[Option[UUID]] =
      lsql"""SELECT user_id FROM sessions WHERE (
            | id || '-' || encode(hmac(id::text, key, 'sha256'), 'hex')
            |) = $cookie
            |"""
        .stripMargin
        .query[UUID]
        .option

    def getUser(cookie: String): ConnectionIO[Option[User]] =
      lsql"""SELECT user_id, username, u_role
            |FROM sessions
            |JOIN users
            |ON user_id = users.id
            |WHERE (
            | sessions.id || '-' || encode(hmac(sessions.id::text, sessions.key, 'sha256'), 'hex')
            |) = $cookie
            |"""
        .stripMargin
        .query[User]
        .option

    def deleteSession(cookie: String): ConnectionIO[Unit] =
      lsql"""DELETE FROM sessions WHERE(
           | id || '-' || encode(hmac(id::text, key, 'sha256'), 'hex')
           |) = ${cookie}
           |"""
        .stripMargin
        .update
        .run
        .void

  }
}
