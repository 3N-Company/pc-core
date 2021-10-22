package endpoints

import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

trait EndpointsModule[F[_]] {
  def all: List[ServerEndpoint[_, _, _, Fs2Streams[F] with WebSockets, F]]
}
