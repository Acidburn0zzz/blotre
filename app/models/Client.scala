package models

import helper.datasources.MorphiaObject
import org.bson.types.ObjectId
import org.mongodb.morphia.annotations._
import org.mongodb.morphia.query.Query
import play.data.validation.Constraints
import java.util.Date
import scala.annotation.meta.field

/**
 *
 */
@Entity
@SerialVersionUID(1)
case class Client(
  @(Id @field)
  var id: ObjectId,

  @Constraints.Required
  @Constraints.MaxLength(255)
  @Constraints.MinLength(3)
  var name: String,

  @Constraints.Required
  @Constraints.MaxLength(255)
  @Constraints.MinLength(3)
  var uri: String,

  @Constraints.Required
  @Constraints.MaxLength(255)
  @Constraints.MinLength(3)
  var blurb: String,

  var clientSecret: String,

  var created: Date,

  var ownerId: ObjectId)

/**
 *
 */
@Entity
@Indexes(Array(new Index(value = "clientId, uri", unique=true)))
@SerialVersionUID(1)
case class ClientRedirectUri(
  @(Id @field)
  var id: ObjectId,

  var clientId: ObjectId,

  @Constraints.Required
  @Constraints.MaxLength(255)
  @Constraints.MinLength(3)
  var uri: String,

  var created: Date)


object Client
{
  private def save[A](obj: A): Option[A] = {
    MorphiaObject.datastore.save[A](obj)
    Some(obj)
  }

  private def clientDb(): Query[Client] =
    MorphiaObject.datastore.createQuery((classOf[Client]))

  private def redirectDb(): Query[ClientRedirectUri] =
    MorphiaObject.datastore.createQuery((classOf[ClientRedirectUri]))

  def createClient(name: String, uri: String, blurb: String, owner: User) =
    save(new Client(null, name, uri, blurb, Crypto.generateToken, new Date(), owner.id))

  def addRedirectUri(client: Client, uri: String, blurb: String, owner: User) =
    save(new ClientRedirectUri(null, client.id, uri, new Date()))

  def validate(clientId: String, clientSecret: String): Boolean =
    (clientDb()
      .filter("clientId =", clientId)
      .filter("clientSecret =", clientSecret)
      .get != null)

  /**
   * Get a client by its id.
   */
  def findById(id: ObjectId): Option[Client] =
    Option(MorphiaObject.datastore.createQuery(classOf[Client])
      .filter("id = ", id)
      .get)

  def findById(id: String): Option[Client] =
    findById(new ObjectId(id))

  /**
   * Ensures that a redirect belongs to a client
   */
  def validateRedirect(client: Client, redirectUri: String): Option[Client] =
    Option(MorphiaObject.datastore.createQuery(classOf[ClientRedirectUri])
      .filter("clientId =", client.id)
      .filter("redirectUri =", redirectUri)
      .get) map { _ =>
        client
      }


}

object Crypto
{
  def generateToken: String = {
    val key = java.util.UUID.randomUUID.toString
    new sun.misc.BASE64Encoder().encode(key.getBytes)
  }
}