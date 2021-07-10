package zio.webhooks

import zio.json._
import zio.webhooks.PersistentServerState.RetryingState

import java.time.{ Duration, Instant }

/**
 * A persistent version of [[WebhookServer.InternalState]] saved on server shutdown and loaded on
 * server restart.
 */
private[webhooks] final case class PersistentServerState(retryingStates: Map[Long, RetryingState])

private[webhooks] object PersistentServerState {
  val empty: PersistentServerState = PersistentServerState(Map.empty)

  /**
   * Persistent version of [[WebhookServer.WebhookState.Retrying]].
   */
  final case class RetryingState(
    sinceTime: Instant,
    lastRetryTime: Instant,
    timeLeft: Duration,
    backoff: Duration,
    attempt: Int
  )

  object RetryingState {
    implicit val decoder: JsonDecoder[RetryingState] = DeriveJsonDecoder.gen
    implicit val encoder: JsonEncoder[RetryingState] = DeriveJsonEncoder.gen
  }

  implicit val decoder: JsonDecoder[PersistentServerState] = DeriveJsonDecoder.gen
  implicit val encoder: JsonEncoder[PersistentServerState] = DeriveJsonEncoder.gen
}
