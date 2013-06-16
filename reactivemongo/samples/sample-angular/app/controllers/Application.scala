package controllers

import scala.concurrent._

import play.api._
import play.api.mvc._

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.Done

import reactivemongo.api._
import reactivemongo.bson.BSONObjectID
import play.modules.reactivemongo._
import play.modules.reactivemongo.json.collection.JSONCollection

import play.autosource.reactivemongo._

import scala.concurrent.ExecutionContext.Implicits.global
import play.api.Play.current

object Application0 extends ReactiveMongoAutoSourceController[JsObject] {
  val coll = db.collection[JSONCollection]("persons")

  def index = Action {
    Ok(views.html.index("ok"))
  }
}

object Application1 extends ReactiveMongoAutoSourceController[JsObject] {

  val coll = db.collection[JSONCollection]("persons")

  override val reader = __.read[JsObject] keepAnd (
    (__ \ "name").read[String] and
    (__ \ "age").read[Int]
  ).tupled
}

case class Person(name: String, age: Int)
object Person{
  implicit val fmt = Json.format[Person]
}

object Application2 extends ReactiveMongoAutoSourceController[Person] {

  val coll = db.collection[JSONCollection]("persons")

  def index = Action {
    Ok(views.html.index("ok"))
  }
}

case class User(name: String)
object User {
  def find(name: String) = Some(User(name))
}

object Application3 extends ReactiveMongoAutoSourceController[Person] {
  def Authenticated(action: User => EssentialAction): EssentialAction = {
    // Let's define a helper function to retrieve a User
    def getUser(request: RequestHeader): Option[User] = {
      request.session.get("user").flatMap(u => User.find(u))
    }

    // Now let's define the new Action
    EssentialAction { request =>
      getUser(request).map(u => action(u)(request)).getOrElse {
        Done(Unauthorized)
      }
    }
  }

  val coll = db.collection[JSONCollection]("persons")

  override def delete(id: BSONObjectID) = Authenticated { _ =>
    super.delete(id)
  }

  override def get(id: BSONObjectID) = Authenticated { _ =>
    super.get(id)
  }

  def index = Action {
    Ok(views.html.index("ok"))
  }

  def login(name: String) = Action {
    Ok("logged in").withSession("user" -> name)
  }

  def logout = Action {
    Ok("logged out").withNewSession
  }
}