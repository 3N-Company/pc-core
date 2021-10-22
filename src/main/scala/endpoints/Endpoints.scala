package endpoints
import distage._

object Endpoints {
  def Module[F[_]: TagK]: ModuleDef = new ModuleDef {
    make[BaseEndpoints[F]].from[BaseEndpoints.Impl[F]]

    make[PhotoEndpoints[F]].from[PhotoEndpoints[F]]
    make[AuthEndpoints[F]].from[AuthEndpoints[F]]


    many[EndpointsModule[F]].ref[PhotoEndpoints[F]]
    many[EndpointsModule[F]].ref[AuthEndpoints[F]]
  }
}
