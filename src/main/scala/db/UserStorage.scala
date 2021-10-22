package db

import cats.{Apply, Monad}
import db.models.{Credentials, User}
import derevo.derive
import doobie.{ConnectionIO, LogHandler}
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, LoggingCompanion}
import tofu.logging.derivation.loggingMidTry
import tofu.syntax.doobie.log.string._
import tofu.syntax.monadic._
import doobie.postgres.implicits._
import izumi.distage.model.definition.Lifecycle
import izumi.fundamentals.platform.functional.Identity
import tofu.Tries
import tofu.doobie.transactor.Txr
import cats.tagless.syntax.functorK._

import java.util.UUID

@derive(representableK, loggingMidTry)
trait UserStorage[F[_]] {
  def init: F[Unit]
  def create(credentials: Credentials): F[UUID]
  def find(credentials: Credentials): F[Option[UUID]]
}

object UserStorage extends LoggingCompanion[UserStorage] {

  def apply[F[_]: UserStorage]: UserStorage[F] = implicitly

  final class Make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]) extends Lifecycle.Of[Identity[*], UserStorage[F]](
    Lifecycle.pure(make[F, DB](txr))
  )



  def make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]): UserStorage[F] =  {
    val sql = EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
    val tx = txr.trans
    sql.mapK(tx)
  }

  //CREATE EXTENSION pgcrypto;
  //CREATE TYPE role AS ENUM ('plain', 'admin');
  final class Impl(implicit lh: LogHandler) extends UserStorage[ConnectionIO] {
    def init: ConnectionIO[Unit] =
      lsql"""
            |
            |CREATE TABLE IF NOT EXISTS users
            |(
            | id       uuid NOT NULL DEFAULT gen_random_uuid(),
            | username text NOT NULL,
            | password text NOT NULL,
            | u_role role NOT NULL DEFAULT 'plain',
            | CONSTRAINT PK_9 PRIMARY KEY ( "id" ),
            | CONSTRAINT ind_51 UNIQUE ( username )
            |)"""
        .stripMargin
        .update
        .run
        .void

    def create(credentials: Credentials): ConnectionIO[UUID] =
      lsql"""INSERT INTO users (username, password) VALUES (
            |  ${credentials.username},
            |  crypt(${credentials.password}, gen_salt('bf'))
            |)"""
        .stripMargin
        .update
        .withUniqueGeneratedKeys[UUID]("id")

    def find(credentials: Credentials): ConnectionIO[Option[UUID]] =
      lsql"""SELECT id
            |  FROM users
            | WHERE username = ${credentials.username}
            |   AND password = crypt(${credentials.password}, password)"""
        .stripMargin
        .query[UUID]
        .option

  }

}
