package psgr.monitoring

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.ActorMaterializer
import play.api.libs.json.Json

import scala.concurrent.duration._

/**
 * @author alari
 * @since 9/3/14
 */
object MonitoringApp extends App with Directives {
  implicit val system = ActorSystem("monitoring")
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val timeout = akka.util.Timeout(1, SECONDS)

  val serviceHandler = system.actorOf(Props[ServiceHandler], "service-handler")

  val routes =
    (get & path(Segment)) { s =>
      ctx =>
        println(s"GOT ERROR $s")
        for {
          ServiceHandler.ErrorAck <- serviceHandler ? ServiceHandler.TriggerError(s)
          r <- ctx.complete(s"Got it for $s!")
        } yield r
    } ~ get {
      ctx =>
        println("GOT STATS REQUEST")
        for {
          ServiceHandler.Stats(e) <- serviceHandler ? ServiceHandler.GetStats
          r <- ctx.complete(s"Hello consul! I got $e errors")
        } yield r
    }

  Http(system).bindAndHandle(routes, interface = System.getProperty("host", "localhost"), port = 8186).foreach(r => println("Bound " + r))
}

class ServiceHandler extends Actor {

  import ServiceHandler._

  var errors: Int = 0

  override def receive: Receive = {
    case GetStats =>
      sender() ! Stats(errors)

    case TriggerError(s) =>
      context.child(s).getOrElse(context.actorOf(Props(classOf[ErrorThrottle], 10 minutes, 8, System.getProperty("token", "93317bef88fb244e76a012d20484dbdc")), s)) ! ErrorThrottle

      sender() ! ErrorAck

      errors += 1
  }

}

object ServiceHandler {

  sealed trait Command

  case object GetStats extends Command

  case class Stats(errors: Int)

  case class TriggerError(service: String) extends Command

  case object ErrorAck

}


class ErrorThrottle(timeout: FiniteDuration, limit: Int, token: String) extends Actor with ActorLogging {
  context.setReceiveTimeout(timeout)

  import context.{dispatcher, system}

  val service = self.path.name

  def receive = below(0)

  def escalate(repeat: Int = 1) = {

    import MonitoringApp.materializer

    Http(system) singleRequest HttpRequest(POST, Uri(s"https://api.flowdock.com/v1/messages/team_inbox/$token"), entity = HttpEntity(`application/json`, Json.obj(
      "source" -> "Monitoring",
      "from_address" -> "noreply@carryx.com",
      "subject" -> s"$service is not stable",
      "content" -> s"Escalating instability for $service (triggered $repeat times without $timeout of silence)",
      "tags" -> Seq(service, "monitoring", "error")
    ).toString())) foreach { resp =>
      log.info(s"Escalated $service to inbox: {}", resp)
    }

    if (repeat == 1) {
      Http(system) singleRequest HttpRequest(POST, Uri(s"https://api.flowdock.com/v1/messages/chat/$token"), entity = HttpEntity(`application/json`, Json.obj(
        "external_user_name" -> "Monitoring",
        "content" -> s"@Mitya! Escalating instability for $service (triggered $repeat times without $timeout of silence)",
        "tags" -> Seq(service, "monitoring", "error")
      ).toString())) foreach { resp =>
        log.info(s"Escalated $service to chat: {}", resp)
      }
    }

  }

  def below(errors: Int): Receive = {
    case ReceiveTimeout =>
      context.stop(self)

    case ErrorThrottle if limit == errors + 1 =>
      escalate()
      context.become(above(errors + 1))

    case ErrorThrottle =>
      context.become(below(errors + 1))
  }

  def above(errors: Int): Receive = {
    case ReceiveTimeout =>
      context.stop(self)

    case ErrorThrottle if (errors + 1) % limit == 0 =>
      escalate((errors + 1) / limit)
      context.become(above(errors + 1))

    case ErrorThrottle =>
      context.become(above(errors + 1))
  }
}

case object ErrorThrottle