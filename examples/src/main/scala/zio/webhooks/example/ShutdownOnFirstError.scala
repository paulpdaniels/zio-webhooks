package zio.webhooks.example

import zhttp.http._
import zhttp.service.Server
import zio._
import zio.console._
import zio.duration._
import zio.magic._
import zio.stream.UStream
import zio.webhooks._
import zio.webhooks.backends.sttp.WebhookSttpClient
import zio.webhooks.testkit._

/**
 * An example of how to shut down the server on the first error encountered.
 */
object ShutdownOnFirstError extends App {

  private lazy val events = UStream
    .iterate(0L)(_ + 1)
    .map { i =>
      WebhookEvent(
        WebhookEventKey(WebhookEventId(i), webhook.id),
        WebhookEventStatus.New,
        s"""{"payload":$i}""",
        Chunk(("Accept", "*/*"), ("Content-Type", "application/json"))
      )
    }
    .take(2) ++ UStream(eventWithoutWebhook)

  private lazy val eventWithoutWebhook = WebhookEvent(
    WebhookEventKey(WebhookEventId(-1), WebhookId(-1)),
    WebhookEventStatus.New,
    s"""{"payload":-1}""",
    Chunk(("Accept", "*/*"), ("Content-Type", "application/json"))
  )

  private val httpApp = HttpApp.collectM {
    case request @ Method.POST -> Root / "endpoint" =>
      ZIO
        .foreach(request.getBodyAsString)(str => putStrLn(s"""SERVER RECEIVED PAYLOAD: "$str""""))
        .as(Response.status(Status.OK))
  }

  private lazy val port = 8080

  private def program = {
    for {
      errorFiber <- WebhookServer.getErrors.use(_.take.flip).fork
      httpFiber  <- Server.start(port, httpApp).fork
      _          <- TestWebhookRepo.createWebhook(webhook)
      _          <- events.schedule(Schedule.fixed(1.second)).foreach(TestWebhookEventRepo.createEvent).fork
      _          <- errorFiber.join.onExit(_ => WebhookServer.shutdown.orDie *> httpFiber.interrupt)
    } yield ()
  }.catchAll {
    case WebhookError.InvalidStateError(_, message) => putStrLnErr(s"Invalid state: $message")
    case WebhookError.MissingWebhookError(id)       => putStrLnErr(s"Missing webhook: $id")
    case WebhookError.MissingEventError(key)        => putStrLnErr(s"Missing event: $key")
    case WebhookError.MissingEventsError(keys)      => putStrLnErr(s"Missing events: $keys")
  }

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program
      .injectCustom(
        TestWebhookEventRepo.test,
        TestWebhookRepo.test,
        TestWebhookStateRepo.test,
        WebhookServer.live,
        WebhookServerConfig.default,
        WebhookSttpClient.live
      )
      .exitCode

  private lazy val webhook = Webhook(
    id = WebhookId(0),
    url = s"http://0.0.0.0:$port/endpoint",
    label = "test webhook",
    WebhookStatus.Enabled,
    WebhookDeliveryMode.SingleAtLeastOnce
  )
}