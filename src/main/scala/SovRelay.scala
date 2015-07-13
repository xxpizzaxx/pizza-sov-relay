import java.util.concurrent.atomic.AtomicInteger

import io.backchat.hookup._
import scala.concurrent.duration._
import dispatch._, Defaults._

import scala.util.{Failure, Try}

object SovRelay extends App {

  case class Broadcast(x: String)

  var lastUpdate: String = "{\"status\": \"starting up\"}"
  var users = new AtomicInteger(0)

  val server = HookupServer(8125) {
    new HookupServerClient {
      def receive = {
        case Connected =>
          send(lastUpdate)
          users.incrementAndGet()
        case TextMessage(text) =>
          println(text)
          send("{\"error\": \"this server cannot perform user-requested actions (except this one)\"}")
        case Disconnected(why) =>
          println("user disconnected")
          println(why)
          users.decrementAndGet()
      }
    }
  }
  server.start

  val system = akka.actor.ActorSystem("system")

  implicit class EitherPimp[L <: Throwable,T](e:Either[L,T]) {
    def toTry:Try[T] = e.fold(Failure(_), util.Success(_))
  }

  def pullLatest(): Unit = {
    val svc = url("https://public-crest-duality.testeveonline.com/sovereignty/campaigns/")
    val res = Http(svc OK as.String)
    res.either.map {
      case Right(r) =>
        println(r)
        lastUpdate = r
        server.broadcast(new TextMessage(r))
      case Left(t) =>
        println(t.getMessage)
        println(t)
        server.broadcast(new TextMessage("{\"status\": \"ccp api is unavailable\"}"))
    }
  }

  system.scheduler.schedule(1 seconds, 30 seconds)(pullLatest)
  println("Scheduled puller")
  server
}
