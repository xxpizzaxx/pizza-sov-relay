import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import com.codahale.metrics.graphite.{Graphite, GraphiteReporter}
import com.codahale.metrics.{MetricFilter, ConsoleReporter, MetricRegistry}
import io.backchat.hookup._
import scala.concurrent.duration._
import dispatch._, Defaults._

import scala.util.{Failure, Try}

object SovRelay extends App {

  // set up metrics
  val metrics = new MetricRegistry()
  val connectedClients = metrics.counter("connected_clients")
  val broadcastsSent = metrics.meter("broadcasts_sent")
  val crestApiProblems = metrics.meter("crest_api_problems")

  val reporter = GraphiteReporter.forRegistry(metrics)
    .prefixedWith("moe.pizza.pizza-sov-relay")
    .convertRatesTo(TimeUnit.SECONDS)
    .convertDurationsTo(TimeUnit.MILLISECONDS)
    .filter(MetricFilter.ALL)
    .build(new Graphite(new InetSocketAddress("localhost", 2003)))
  reporter.start(1, TimeUnit.MINUTES)

  // set up internal state
  var lastUpdate: String = "{\"status\": \"starting up\"}"

  // set up websocket server
  val server = HookupServer(8125) {
    new HookupServerClient {
      def receive = {
        case Connected =>
          send(lastUpdate)
          connectedClients.inc()
        case TextMessage(text) => ()
        case Disconnected(why) =>
          connectedClients.dec()
      }
    }
  }
  server.start

  // set up the API poller
  val system = akka.actor.ActorSystem("system")

  implicit class EitherPimp[L <: Throwable,T](e:Either[L,T]) {
    def toTry:Try[T] = e.fold(Failure(_), util.Success(_))
  }

  def pullLatest(): Unit = {
    val svc = url("https://public-crest-duality.testeveonline.com/sovereignty/campaigns/")
    val res = Http(svc OK as.String)
    res.either.map {
      case Right(r) =>
        lastUpdate = r
        server.broadcast(new TextMessage(r))
        broadcastsSent.mark()
      case Left(t) =>
        server.broadcast(new TextMessage("{\"status\": \"ccp api is unavailable\"}"))
        crestApiProblems.mark()
    }
  }

  system.scheduler.schedule(0 seconds, 30 seconds)(pullLatest)
}
