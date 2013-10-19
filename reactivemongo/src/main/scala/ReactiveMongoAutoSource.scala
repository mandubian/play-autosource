/**
  * Copyright 2013 Pascal Voitot (@mandubian)
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *     http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package play.autosource.reactivemongo

import scala.concurrent._

import reactivemongo.bson._
import reactivemongo.api._

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.syntax._
import play.api.libs.functional.syntax._
import play.api.libs.json.extensions._
import play.api.libs.iteratee.Enumerator
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection._
import play.modules.reactivemongo.json.BSONFormats._

import play.autosource.core._

object `package` {
  implicit def BSONObjectIdBindable(implicit stringBinder: PathBindable[String]) =
    new PathBindable[BSONObjectID] {
      override def bind(key: String, value: String): Either[String, BSONObjectID] = {
        for {
          id     <- stringBinder.bind(key, value).right
          bsonid <- BSONObjectID.parse(id).map(Right(_)).getOrElse(Left("Can't parse id")).right
        } yield bsonid
      }
      override def unbind(key: String, bsonid: BSONObjectID): String = {
        stringBinder.unbind(key, bsonid.toString)
      }
    }
}

class ReactiveMongoAutoSource[T](coll: JSONCollection)(implicit format: Format[T]) extends AutoSource[T, BSONObjectID, JsObject, JsObject] {
  def insert(t: T)(implicit ctx: ExecutionContext): Future[BSONObjectID] = {
    val id = BSONObjectID.generate
    val obj = format.writes(t).as[JsObject]
    obj \ "_id" match {
      case _:JsUndefined =>
        coll.insert(obj ++ Json.obj("_id" -> id))
            .map{ _ => id }

      case _ => coll.insert(obj).map{ _ => id }
    }
  }

  def get(id: BSONObjectID)(implicit ctx: ExecutionContext): Future[Option[(T, BSONObjectID)]] = {
    coll.find(Json.obj("_id" -> id)).cursor[JsObject].headOption.map(_.map( js => (js.as[T], id)))
  }

  def delete(id: BSONObjectID)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.remove(Json.obj("_id" -> id)).map( _ => () )
  }

  def update(id: BSONObjectID, t: T)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.update(
      Json.obj("_id" -> id),
      Json.obj("$set" -> t)
    ).map{ _ => () }
  }

  def updatePartial(id: BSONObjectID, upd: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.update(
      Json.obj("_id" -> id),
      Json.obj("$set" -> upd)
    ).map{ _ => () }
  }

  def batchInsert(elems: Enumerator[T])(implicit ctx: ExecutionContext): Future[Int] = {
    val enum = elems.map{ t =>
      val id = BSONObjectID.generate
      val obj = format.writes(t).as[JsObject]
      obj \ "_id" match {
        case _:JsUndefined => Json.obj("_id" -> id) ++ obj
        case _ => obj
      }
    }

    coll.bulkInsert(enum)
  }

  def find(sel: JsObject, limit: Int = 0, skip: Int = 0)(implicit ctx: ExecutionContext): Future[Traversable[(T, BSONObjectID)]] = {
    val cursor = coll.find(sel).options(QueryOpts().skip(skip)).cursor[JsObject]
    val l = if(limit!=0) cursor.collect[Traversable](limit) else cursor.collect[Traversable]()
    l.map(_.map( js => (js.as[T], (js \ "_id").as[BSONObjectID])))
  }

  def findStream(sel: JsObject, skip: Int = 0, pageSize: Int = 0)(implicit ctx: ExecutionContext): Enumerator[TraversableOnce[(T, BSONObjectID)]] = {
    val cursor = coll.find(sel).options(QueryOpts().skip(skip)).cursor[JsObject]
    val enum = if(pageSize !=0) cursor.enumerateBulks(pageSize) else cursor.enumerateBulks()
    enum.map(_.map( js => (js.as[T], (js \ "_id").as[BSONObjectID])))
  }

  def batchDelete(sel: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.remove(sel).map( _ => () )
  }

  def batchUpdate(sel: JsObject, upd: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.update(
      sel,
      Json.obj("$set" -> upd),
      multi = true
    ).map{ _ => () }
  }

}

abstract class ReactiveMongoAutoSourceController[T](implicit ctx: ExecutionContext, format: Format[T])
  extends AutoSourceRouterContoller[BSONObjectID]
  with MongoController {

  def coll: JSONCollection

  lazy val res = new ReactiveMongoAutoSource[T](coll)

  val queryReader: Reads[JsObject] = implicitly[Reads[JsObject]]
  val updateReader: Reads[JsObject] = implicitly[Reads[JsObject]]
  val batchReader: Reads[(JsObject, JsObject)] = (
    (__ \ "query").read(queryReader) and
    (__ \ "update").read(updateReader)
  ).tupled

  val limitReader: Reads[Int] = (__ \ "limit").read[Int]
  val pageSizeReader: Reads[Int] = (__ \ "pageSize").read[Int]
  val skipReader: Reads[Int] = (__ \ "skip").read[Int]

  implicit val writerWithId = Writes[(T, BSONObjectID)] {
    case (t, id) =>
      val ser = format.writes(t).as[JsObject].updateAllKeyNodes{
        case ( _ \ "_id", value ) => ("id" -> value \ "$oid")
      }
      if((__ \ "id")(ser).isEmpty) ser.as[JsObject] ++ Json.obj("id" -> id.stringify)
      else ser
  }
  val idWriter = Writes[BSONObjectID] { id =>
    Json.obj("id" -> id.stringify)
  }

  def insert: EssentialAction = Action(parse.json){ request =>
    Json.fromJson[T](request.body).map{ t =>
      Async{
        res.insert(t).map{ id => Ok(Json.toJson(id)(idWriter)) }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def get(id: BSONObjectID): EssentialAction = Action{
    Async{
      res.get(id).map{
        case None    => NotFound(s"ID ${id.stringify} not found")
        case Some(tid) => Ok(Json.toJson(tid))
      }
    }
  }

  def delete(id: BSONObjectID) = Action{
    Async{
      res.delete(id).map{ le => Ok(Json.toJson(id)(idWriter)) }
    }
  }

  def update(id: BSONObjectID): EssentialAction = Action(parse.json){ request =>
    Json.fromJson[T](request.body).map{ t =>
      Async{
        res.update(id, t).map{ _ => Ok(Json.toJson(id)(idWriter)) }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def updatePartial(id: BSONObjectID) = Action.async(parse.json){ request =>
    Json.fromJson[JsObject](request.body)(updateReader).map{ upd =>
      res.updatePartial(id, upd).map{ _ => Ok(Json.toJson(id)(idWriter)) }
    }.recoverTotal{ e => Future.successful(BadRequest(JsError.toFlatJson(e))) }
  }

  def batchInsert: EssentialAction = Action(parse.json){ request =>
    Json.fromJson[Seq[T]](request.body).map{ elems =>
      Async{
        res.batchInsert(Enumerator(elems:_*)).map{ nb => Ok(Json.obj("nb" -> nb)) }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  private def parseQuery[T](request: Request[T]): JsValue = {
    request.queryString.get("q") match {
      case None =>
        request.body match {
          case AnyContentAsJson(json) => json
          case AnyContentAsEmpty => Json.obj()
          case _ => throw new RuntimeException("Body in Request isn't Json")
        }

      case Some(q)   =>
        q.headOption.map{ str =>
          try {
            Json.parse(str)
          } catch { case e: Throwable => throw new RuntimeException("queryparam 'q' isn't Json") }
        }.getOrElse(Json.obj())
    }
  }

  def find = Action.async { request =>
    val json: JsValue = parseQuery(request)
    val limit = request.queryString.get("limit").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)

    Json.fromJson[JsObject](json)(queryReader).map{ js =>
      Async{
        res.find(js, limit, skip).map{ s =>
          Ok(Json.toJson(s))
        }
      }
    }.recoverTotal{ e => Future.successful(BadRequest(JsError.toFlatJson(e))) }
  }

  def findStream = Action { request =>
    val json: JsValue = parseQuery(request)
    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val pageSize = request.queryString.get("pageSize").flatMap(_.headOption.map(_.toInt)).getOrElse(0)

    Json.fromJson[JsObject](json)(queryReader).map{ js =>
      Ok.stream(
        res.findStream(js, skip, pageSize)
           .map( it => Json.toJson(it.toTraversable) )
           .andThen(Enumerator.eof)
      )
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def batchDelete = Action{ request =>
    val json: JsValue = parseQuery(request)
    Json.fromJson[JsObject](json)(queryReader).map{ js =>
      Async {
        res.batchDelete(js).map{ _ => Ok("deleted") }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def batchUpdate = Action{ request =>
    val json: JsValue = parseQuery(request)
    Json.fromJson[(JsObject, JsObject)](json)(batchReader).map{
      case (q, upd) => Async {
        println(s"q:$q upd:$upd")
        res.batchUpdate(q, upd).map{ _ => Ok("updated") }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

}

