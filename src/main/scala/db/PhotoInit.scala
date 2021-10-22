package db

import cats.effect.{Blocker, ContextShift, Sync}
import common.Config
import db.repository.PhotoStorage
import tofu.syntax.monadic._

import java.nio.file.Path

final class PhotoInit[F[_]: Sync: ContextShift: PhotoStorage](blocker: Blocker, config: Config) {

  def paths: F[List[String]] = fs2.io.file.directoryStream[F] (blocker, Path.of(config.photoFolder))
    .map(_.toString)
    .compile
    .toList

  def init: F[Unit] = paths.flatMap(PhotoStorage[F].insertMany)

}
