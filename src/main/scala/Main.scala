import cats.Monad
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, Effect, ExitCode, IO, IOApp, Sync}
import common.Config
import db.{DB, PhotoStorage, SessionStorage, SubmissionStorage, UserStorage}
import distage._
import endpoints.{Endpoints, EndpointsModule}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import org.http4s.implicits._
import sttp.tapir._
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.server.http4s._
import tofu.{Delay, Tries}
import tofu.doobie.{ConnectionCIO, LiftConnectionIO}
import tofu.generate.GenUUID
import tofu.lift.UnliftIO
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import cats.syntax.traverse._
import db.models.Credentials
import tofu.syntax.monadic._

import scala.concurrent.ExecutionContext


object Main extends IOApp{

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  //dirty
  def prepareDB(locator: Locator): IO[Unit] =
    for{
      users <- locator.get[UserStorage[IO]].pure[IO]
      photos = locator.get[PhotoStorage[IO]]
      sessions = locator.get[SessionStorage[IO]]
      submissions = locator.get[SubmissionStorage[IO]]
      _ <- users.init
      _ <- photos.init
      _ <- sessions.init
      _ <- submissions.init
      //_ <- users.create(Credentials("admin", "admin"))
    } yield ()


  def app(locator: Locator): IO[Unit] =
    for {
      endpoints <- IO.delay(locator.get[Set[EndpointsModule[IO]]])
      config = locator.get[Config]
      _ <- prepareDB(locator)
      serverEndpoints = endpoints.toList.flatMap(_.all)
      openapiYaml = OpenAPIDocsInterpreter()
        .serverEndpointsToOpenAPI(serverEndpoints, "endpoints", "1")
        .toYaml
      swagger = new SwaggerHttp4s(openapiYaml)
      routes = Http4sServerInterpreter[IO].toRoutes(serverEndpoints)
      _ <- BlazeServerBuilder[IO](ec)
        .withHttpApp(Router("/" -> routes, "/api" -> swagger.routes[IO]).orNotFound)
        .bindHttp(config.serverPort, "localhost")
        .serve
        .compile
        .drain
    } yield ()

  def Program[F[_]: TagK: ConcurrentEffect: ContextShift, DB[_]: TagK: Delay: Monad: ContextShift: UnliftIO: Tries: LiftConnectionIO] = {
    CommonModule[F] ++
      Config.Module[F] ++
      DB.Module[F, DB] ++
      Endpoints.Module[F]
  }

  def CommonModule[F[_]: TagK: Delay: Sync]: ModuleDef = new ModuleDef {
    make[GenUUID[F]].from(GenUUID.syncGenUUID[F])
    make[Blocker].fromResource(Blocker[F])
  }



  override def run(args: List[String]): IO[ExitCode] =
    Injector[IO]()
      .produce(Program[IO, ConnectionCIO[IO, *]], Roots.Everything)
      .use {app}
      .as(ExitCode.Success)
}
