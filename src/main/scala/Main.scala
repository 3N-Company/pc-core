import cats.{Monad, MonadError}
import cats.effect.{Blocker, ConcurrentEffect, ContextShift, ExitCode, IO, IOApp, Sync}
import common.Config
import db.{DB, Migrator, PhotoInit}
import distage._
import endpoints.{Endpoints, EndpointsModule}
import external.External
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware.CORS
import sttp.client3.SttpBackend
import sttp.client3.http4s.Http4sBackend
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.http4s._
import sttp.tapir.swagger.http4s.SwaggerHttp4s
import tofu.doobie.{ConnectionCIO, LiftConnectionIO}
import tofu.generate.GenUUID
import tofu.lift.UnliftIO
import tofu.logging.Logging
import tofu.{Delay, Fire, Raise, Tries}

import scala.concurrent.ExecutionContext

object Main extends IOApp {

  implicit val ec: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global
  // dirty

  def app(locator: Locator): IO[Unit] =
    for {
      endpoints      <- IO.delay(locator.get[Set[EndpointsModule[IO]]])
      _              <- locator.get[Migrator[IO]].migrate
      _              <- locator.get[PhotoInit[IO]].init
      config          = locator.get[Config]
      serverEndpoints = endpoints.toList.flatMap(_.all)
      openapiYaml     = OpenAPIDocsInterpreter()
                          .serverEndpointsToOpenAPI(serverEndpoints, "endpoints", "1")
                          .toYaml
      swagger         = new SwaggerHttp4s(openapiYaml)
      routes          = Http4sServerInterpreter[IO].toRoutes(serverEndpoints)
      corsRoutes      = CORS(routes)
      _              <- BlazeServerBuilder[IO](ec)
                          .enableHttp2(true)
                          .withHttpApp(
                            Router("/" -> corsRoutes, "/api" -> swagger.routes[IO]).orNotFound
                          )
                          .bindHttp(config.serverPort, "0.0.0.0")
                          .serve
                          .compile
                          .drain
    } yield ()

  def Program[F[_]: TagK: ConcurrentEffect: ContextShift, DB[
      _
  ]: TagK: Delay: Monad: ContextShift: UnliftIO: Tries: LiftConnectionIO] = {
    CommonModule[F] ++
      Config.Module[F] ++
      DB.Module[F, DB] ++
      Endpoints.Module[F] ++
      External.Module[F]
  }

  def CommonModule[F[_]: TagK: Delay: Sync: ConcurrentEffect: ContextShift: MonadError[*[_], Throwable]: Fire]
      : ModuleDef = new ModuleDef {
    make[GenUUID[F]].from(GenUUID.syncGenUUID[F])
    make[Blocker].fromResource(Blocker[F])
    make[SttpBackend[F, Any]].fromResource { blocker: Blocker =>
      Http4sBackend.usingDefaultBlazeClientBuilder[F](blocker)
    }
    make[Raise[F, Throwable]].from(implicitly[Raise[F, Throwable]])
    make[Fire[F]].from(implicitly[Fire[F]])
    make[Logging.Make[F]].from(Logging.Make.plain[F])
  }

  override def run(args: List[String]): IO[ExitCode] =
    Injector[IO]()
      .produce(Program[IO, ConnectionCIO[IO, *]], Roots.Everything)
      .use { app }
      .as(ExitCode.Success)
}
