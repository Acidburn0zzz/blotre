package Actors

import akka.actor._
import akka.contrib.pattern.{DistributedPubSubExtension, DistributedPubSubMediator}
import akka.pattern.{ask}
import akka.util.Timeout
import helper._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.Logger
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class GetCollection(uri: String)
case class GetCollectionResponse(actor: ActorRef)


/**
 * Manages all collections in the system.
 *
 * Attempts to kill collections that are no longer referenced.
 */
class CollectionSupervisor extends Actor
{
  import akka.contrib.pattern.DistributedPubSubMediator._

  private lazy val mediator = DistributedPubSubExtension.get(Akka.system).mediator

  private var subscriptions: Map[String, Set[ActorRef]] = Map()

  /**
   * Topic used to register a collection.
   */
  private def getTopic(path: String): Option[String] = {
    val normalizePath = ActorHelper.normalizeName(path)
    if (normalizePath.isEmpty) None else Some("@collection/" + normalizePath)
  }

  def receive = {
    case Subscribe(path, group, subscriber) =>
      onSubscribe(path, subscriber)
      getTopic(path) map { topic =>
        mediator ! Subscribe(topic, group, subscriber)
      }

    case Unsubscribe(path, group, subscriber) =>
      onUnsubscribe(path, subscriber)
      getTopic(path) map { topic =>
        mediator ! Unsubscribe(topic, group, subscriber)
      }

    case Publish(path, msg, toEachGroup) =>
      getTopic(path) map { topic =>
        mediator ! Publish(topic, msg, toEachGroup)
      }

    case GetCollection(uri) =>
      getOrCreateChild(uri) map {
        sender ! GetCollectionResponse(_)
      } getOrElse {
        sender ! GetCollectionResponse(null)
      }
  }

  /**
   * Get an existing child value or create a new one.
   */
  private def getOrCreateChild(uri: String) =
    ActorHelper.normalizeName(uri) map { name =>
      context.child(name).getOrElse(context.actorOf(CollectionActor.props(uri), name = name))
    }

  /**
   * Invoked when a subscriber has been successfully registered.
   */
  private def onSubscribe(path: String, subscriber: ActorRef): Unit = {
    getOrCreateChild(path) // ensure child exists
    subscriptions += ((path, subscriptions.getOrElse(path, Set()) + subscriber))
  }

  /**
   * Invoked when a subscriber has been successfully unregistered.
   *
   * If the subscriber count reaches zero, kicks off a job to potentially kill the
   * child collection actor if no other subscriptions are added.
   */
  private def onUnsubscribe(path: String, subscriber: ActorRef): Unit = {
    val collectionSubscribers = subscriptions.getOrElse(path, Set()) - subscriber
    subscriptions += ((path, collectionSubscribers))

    if (collectionSubscribers.size == 0) {
      subscriptions -= path
      tryRemoveChild(path)
    }
  }

  private def tryRemoveChild(path:String) =
    context.system.scheduler.scheduleOnce(5 seconds) {
      subscriptions.get(path) map { subscribers =>
        if (subscribers.size == 0) {
          removeChild(path)
        }
      } orElse {
        removeChild(path)
      }
    }

  private def removeChild(path: String) = {
    context.child(path) map {
      _ ! PoisonPill
    }
  }
}

object CollectionSupervisor
{
  /**
   * Get the Akka path of a stream collection.
   */
  private def getStreamTopic(path: models.StreamUri): Option[String] =
    ActorHelper.normalizeName(path.value)
      .filterNot(_.isEmpty)
      .map("streams/" + _)

  private def getStreamTopic(path: String): Option[String] =
    models.StreamUri.fromString(path).flatMap(getStreamTopic)

  /**
   * Get the Akka path of a tagcollection.
   */
  private def getTagTopic(tag: models.StreamTag): Option[String] =
    Some(ActorHelper.normalizeName(tag.value))
      .filterNot(_.isEmpty)
      .map("tags/" + _)

  def props(): Props = Props(new CollectionSupervisor())

  lazy val supervisor = Akka.system.actorOf(props())

  implicit val timeout = Timeout(5 seconds)

  /**
   * Get the actor for a collection.
   */
  private def getCollection(uri: String): Future[ActorRef] =
    ask(supervisor, GetCollection(uri)).mapTo[GetCollectionResponse].map(_.actor)

  /**
   * Get the in-memory state of a stream collection.
   */
  def getStreamCollection(uri: models.StreamUri, limit: Int, offset: Int): Future[List[String]] =
    getStreamTopic(uri) map { topic =>
      getCollection(topic) flatMap { collection =>
        ask(collection, GetCollectionStatus(limit, offset)).mapTo[List[String]]
      }
    } getOrElse {
      Future.successful(null)
    }

  /**
   * Get the in-memory state of a tag collection.
   */
  def getTagCollection(tag: models.StreamTag, limit: Int, offset: Int): Future[List[String]] =
    getTagTopic(tag) map { topic =>
      getCollection(topic) flatMap { collection =>
        ask(collection, GetCollectionStatus(limit, offset)).mapTo[List[String]]
      }
    } getOrElse {
      Future.successful(null)
    }

  /**
   * Subscribe an actor to a collection's events.
   */
  def subscribeCollection(subscriber: ActorRef, path: String): Unit =
      supervisor ! DistributedPubSubMediator.Subscribe(path, subscriber)

  /**
   * Unsubscribe an actor from a collection's events.
   */
  def unsubscribeCollection(subscriber: ActorRef, path: String): Unit =
    supervisor ! DistributedPubSubMediator.Unsubscribe(path, subscriber)

  /**
   * Broadcast an event for a collection.
   */
  def broadcast[A](path: String, event: A): Unit =
    supervisor ! DistributedPubSubMediator.Publish(path, event)
}