package db

import cats.{Apply, Monad}
import derevo.derive
import doobie.ConnectionIO
import doobie.util.log.LogHandler
import tofu.doobie.LiftConnectionIO
import tofu.doobie.log.EmbeddableLogHandler
import tofu.higherKind.derived.representableK
import tofu.logging.{Logging, LoggingCompanion}
import tofu.logging.derivation.{loggingMid, loggingMidTry}
import tofu.syntax.doobie.log.string._
import tofu.syntax.monadic._
import distage._
import izumi.fundamentals.platform.functional.Identity
import tofu.Tries
import tofu.doobie.transactor.Txr
import cats.tagless.syntax.functorK._

@derive(representableK, loggingMidTry)
trait PhotoStorage[F[_]] {
  def init: F[Unit]
  def insert(path: String): F[Int]
  def find(id: Int): F[Option[String]]
}

object PhotoStorage extends LoggingCompanion[PhotoStorage] {

  def apply[F[_]: PhotoStorage]: PhotoStorage[F] = implicitly



  final class Make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]) extends Lifecycle.Of[Identity[*], PhotoStorage[F]](
    Lifecycle.pure(make[F, DB](txr))
  )



  def make[F[_]: Apply, DB[_]: Monad: LiftConnectionIO: EmbeddableLogHandler: Logging.Make: Tries](txr: Txr[F, DB]): PhotoStorage[F] = {
    val sql = EmbeddableLogHandler[DB].embedLift(implicit lh => new Impl).attachErrLogs
    val tx = txr.trans
    sql.mapK(tx)
  }


  final class Impl(implicit lh: LogHandler) extends PhotoStorage[ConnectionIO] {
    def init: ConnectionIO[Unit] =
      lsql"""CREATE TABLE IF NOT EXISTS photo
            |(
            | id   serial NOT NULL,
            | file_path varchar(50) NOT NULL,
            | CONSTRAINT PK_5 PRIMARY KEY ( "id" )
            |)"""
        .stripMargin
        .update
        .run
        .void

    def insert(path: String): ConnectionIO[Int] =
      lsql"""INSERT INTO photo (file_path) VALUES($path)"""
        .update
        .withUniqueGeneratedKeys[Int]("id")

    def find(id: Int): ConnectionIO[Option[String]] =
      lsql"""SELECT file_path
             |  FROM photo
             | WHERE id = $id"""
        .stripMargin
        .query[String]
        .option

    def getAllPagedSorted(offset: Int, limit: Int)
  }

}
