package carryx.monitoring

import akka.actor._
import akka.io.IO
import org.joda.time.DateTime
import play.api.libs.json.Json
import spray.can.Http
import spray.http.ContentTypes._
import spray.http.HttpMethods._
import spray.http.{HttpEntity, HttpRequest, HttpResponse, Uri}

import scala.concurrent.duration._

/**
 * @author alari
 * @since 9/3/14
 */
object MonitoringApp extends App {
  implicit val system = ActorSystem("monitoring")

  val listener: ActorRef = system.actorOf(Props[Listener], "listener")
}

class Listener extends Actor {

  import context.system

  var errors: Int = 0

  override def receive: Receive = {
    case _: Http.Connected =>
      sender() ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      sender() ! HttpResponse(entity = s"Hello consul! I'm ok, got $errors error pings, now "+DateTime.now())

    case HttpRequest(GET, Uri.Path(uri), _, _, _) =>
      val s = uri.drop(1)
      context.child(s).getOrElse(context.actorOf(Props(classOf[ErrorThrottle], 1 minute, 4, System.getProperty("token", "93317bef88fb244e76a012d20484dbdc")), s)) ! ErrorThrottle

      sender() ! HttpResponse(entity = "Got it")

      errors += 1
  }

  IO(Http) ! Http.Bind(self, interface = System.getProperty("host", "localhost"), port = 8186)
}

class ErrorThrottle(timeout: FiniteDuration, limit: Int, token: String) extends Actor {
  context.setReceiveTimeout(timeout)
  import context.system

  val service = self.path.name

  def receive = below(0)

  def escalate(repeat: Int = 1) = {

    IO(Http) ! HttpRequest(POST, Uri(s"https://api.flowdock.com/v1/messages/team_inbox/$token"), entity = HttpEntity(`application/json`, Json.obj(
      "source" -> "Monitoring",
      "from_address" -> "noreply@carryx.com",
      "subject" -> s"$service is not stable",
      "content" -> s"Escalating instability for $service (triggered $repeat times without $timeout of silence)",
      "tags" -> Seq(service, "monitoring", "error")
    ).toString()))

    if(repeat == 1) {
      IO(Http) ! HttpRequest(POST, Uri(s"https://api.flowdock.com/v1/messages/chat/$token"), entity = HttpEntity(`application/json`, Json.obj(
        "external_user_name" -> "Monitoring",
        "content" -> s"@Mitya! Escalating instability for $service (triggered $repeat times without $timeout of silence)",
        "tags" -> Seq(service, "monitoring", "error")
      ).toString()))
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