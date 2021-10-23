package db

import cats.effect.{Blocker, ContextShift, Sync}
import cats.syntax.traverse._
import common.Config
import db.repository.PhotoStorage
import external.Colorization
import tofu.syntax.monadic._

import java.nio.file.Path

final class PhotoInit[F[_]: Sync: ContextShift: PhotoStorage: Colorization](blocker: Blocker, config: Config) {

  def paths: F[List[String]] = fs2.io.file
    .directoryStream[F](blocker, Path.of(config.photoFolder))
    .map(_.toString)
    .compile
    .toList

  def init: F[Unit] = paths.flatTap(x => x.traverse(Colorization[F].colorize(_))).flatMap(PhotoStorage[F].insertMany)

}
