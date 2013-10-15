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
  def coll = db.collection[JSONCollection]("persons")

  def index = Action {
    Ok(views.html.index("ok"))
  }
}

object Application1 extends ReactiveMongoAutoSourceController[JsObject] {

  def coll = db.collection[JSONCollection]("persons")

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

  def coll = db.collection[JSONCollection]("persons")

  def index = Action {
    Ok(views.html.index("ok"))
  }
}

case class User(name: String)
object User {
  def find(name: String) = Some(User(name))
}

object Application3 extends ReactiveMongoAutoSourceController[Person] {
  case class AuthenticatedUserRequest[A](
    user: User,
    request:  Request[A]
  ) extends WrappedRequest[A](request)

  object Authenticated extends ActionBuilder[AuthenticatedUserRequest] {
    def getUser(request: RequestHeader): Option[User] = {
      request.session.get("user").flatMap(u => User.find(u))
    }

    def invokeBlock[A](request: Request[A], block: AuthenticatedUserRequest[A] => Future[SimpleResult]): Future[SimpleResult] = {
      getUser(request) match {
        case Some(user)  => block(AuthenticatedUserRequest(user, request))
        case None        => Future.successful(Unauthorized)
      }
    }
  }

  def coll = db.collection[JSONCollection]("persons")

  override def delete(id: BSONObjectID) = Authenticated.async { request =>
    super.delete(id)(request)
  }

  override def get(id: BSONObjectID) = Authenticated.async { request =>
    super.get(id)(request)
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