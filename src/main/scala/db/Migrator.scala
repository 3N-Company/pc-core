package db

import cats.effect.Sync
import common.Config
import doobie.util.transactor.Transactor
import org.flywaydb.core.Flyway

final class Migrator[F[_]: Sync](trans: Transactor[F], config: Config) {
  def migrate: F[Unit] = trans.configure { _ =>
    Sync[F].delay {
      val flyway = Flyway
        .configure()
        .dataSource(config.db.connectionString, config.db.user, config.db.pass)
        .load()
      flyway.migrate()
      ()
    }
  }
}
