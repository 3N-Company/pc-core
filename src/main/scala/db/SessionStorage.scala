package db

import cats.{Apply, Monad}
import db.models.User
import derevo.derive
import tofu.higherKind.derived.representableK
import tofu.logging.derivation.loggingMidTry
import doobie.postgres.implicits._
import tofu.doobie.transactor.Txr
import tofu.logging.LoggingCompanion
import tofu.syntax.monadic._
import cats.tagless.syntax.functorK._

import java.util.UUID

@derive(representableK, loggingMidTry)
trait SessionStorage[F[_]] {
  def init: F[Unit]
  def createSessionCookie(userId: UUID): F[Option[String]]
  def getUserId(cookie: String): F[Option[UUID]]
  def getUser(cookie: String): F[Option[User]]
  def deleteSession(cookie: String): F[Unit]
}

object SessionStorage extends LoggingCompanion[SessionStorage] {

  def apply[F[_]: SessionStorage]: SessionStorage[F] = implicitly

  def make[F[_]: Apply, DB[_]: Monad](sessionSql: SessionSql[DB], txr: Txr[F, DB]): SessionStorage[F] = {
    val impl = new Impl[DB](sessionSql): SessionStorage[DB]
    val tx = txr.trans
    impl.mapK(tx)
  }


  final class Impl[DB[_]: Monad](sessionSql: SessionSql[DB]) extends SessionStorage[DB] {
    def init: DB[Unit] = sessionSql.init

    def createSessionCookie(userId: UUID): DB[Option[String]] =
      sessionSql.createSession(userId) >>= sessionSql.createCookie

    def getUserId(cookie: String): DB[Option[UUID]] = sessionSql.getUserId(cookie)

    def getUser(cookie: String): DB[Option[User]] = sessionSql.getUser(cookie)

    def deleteSession(cookie: String): DB[Unit] = sessionSql.deleteSession(cookie)
  }

}
