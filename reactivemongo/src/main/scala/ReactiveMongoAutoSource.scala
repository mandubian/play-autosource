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

import play.autosource.core._

import scala.concurrent.{ExecutionContext, Future}

import reactivemongo.bson.BSONObjectID
import reactivemongo.api.QueryOpts

import play.api.Play
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.json.extensions._
import play.api.libs.iteratee.Enumerator

import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.json.BSONFormats._


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
  override def insert(t: T)(implicit ctx: ExecutionContext): Future[BSONObjectID] = {
    val id = BSONObjectID.generate
    val obj = format.writes(t).as[JsObject]
    obj \ "_id" match {
      case _:JsUndefined =>
        coll.insert(obj ++ Json.obj("_id" -> id))
            .map{ _ => id }

      case _ => coll.insert(obj).map{ _ => id }
    }
  }

  override def get(id: BSONObjectID)(implicit ctx: ExecutionContext): Future[Option[(T, BSONObjectID)]] = {
    coll.find(Json.obj("_id" -> id)).cursor[JsObject].headOption.map(_.map( js => (js.as[T], id)))
  }

  override def delete(id: BSONObjectID)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.remove(Json.obj("_id" -> id)).map( _ => () )
  }

  override def update(id: BSONObjectID, t: T)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.update(
      Json.obj("_id" -> id),
      Json.obj("$set" -> t)
    ).map{ _ => () }
  }

  override def updatePartial(id: BSONObjectID, upd: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.update(
      Json.obj("_id" -> id),
      Json.obj("$set" -> upd)
    ).map{ _ => () }
  }

  override def batchInsert(elems: Enumerator[T])(implicit ctx: ExecutionContext): Future[Int] = {
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

  override def find(sel: JsObject, limit: Int = 0, skip: Int = 0)(implicit ctx: ExecutionContext): Future[Traversable[(T, BSONObjectID)]] = {
    val cursor = coll.find(sel).options(QueryOpts().skip(skip)).cursor[JsObject]
    val l = if(limit!=0) cursor.collect[Traversable](limit) else cursor.collect[Traversable]()
    l.map(_.map( js => (js.as[T], (js \ "_id").as[BSONObjectID])))
  }

  override def findStream(sel: JsObject, skip: Int = 0, pageSize: Int = 0)(implicit ctx: ExecutionContext): Enumerator[TraversableOnce[(T, BSONObjectID)]] = {
    val cursor = coll.find(sel).options(QueryOpts().skip(skip)).cursor[JsObject]
    val enum = if(pageSize !=0) cursor.enumerateBulks(pageSize) else cursor.enumerateBulks()
    enum.map(_.map( js => (js.as[T], (js \ "_id").as[BSONObjectID])))
  }

  override def batchDelete(sel: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.remove(sel).map( _ => () )
  }

  override def batchUpdate(sel: JsObject, upd: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    coll.update(
      sel,
      Json.obj("$set" -> upd),
      multi = true
    ).map{ _ => () }
  }

}

abstract class ReactiveMongoAutoSourceController[T](implicit ctx: ExecutionContext, format: Format[T])
  extends AutoSourceController[BSONObjectID]
  with MongoController {

  def coll: JSONCollection

  /** Override this to customize how JsErrors are reported.
    * The implementation should call onBadRequest
    */
  protected def onJsError(request: RequestHeader)(jsError: JsError): Future[SimpleResult] =
    onBadRequest(request, JsError.toFlatJson(jsError).toString)


  /** Override to customize deserialization and add validation. */
  protected val reader: Reads[T]  = format
  /** Override to customize serialization. */
  protected val writer: Writes[T] = format

  lazy val res = new ReactiveMongoAutoSource[T](coll)(Format(reader, writer))

  /** Override to cutomize deserialization of queries. */
  protected val queryReader: Reads[JsObject] = implicitly[Reads[JsObject]]

  /** Override to cutomize deserialization of updates. */
  protected val updateReader: Reads[JsObject] = implicitly[Reads[JsObject]]

  /** Override to cutomize deserialization of queries and batch updates. */
  protected val batchReader: Reads[(JsObject, JsObject)] = (
    (__ \ "query").read(queryReader) and
    (__ \ "update").read(updateReader)
  ).tupled


  private implicit val writerWithId = Writes[(T, BSONObjectID)] {
    case (t, id) =>
      val ser = writer.writes(t).as[JsObject].updateAllKeyNodes{
        case ( _ \ "_id", value ) => ("id" -> value \ "$oid")
      }
      if((__ \ "id")(ser).isEmpty) ser.as[JsObject] ++ Json.obj("id" -> id.stringify)
      else ser
  }
  private implicit val idWriter = Writes[BSONObjectID] { id =>
    Json.obj("id" -> id.stringify)
  }

  private def bodyReader[A](reader: Reads[A]): BodyParser[A] =
    BodyParser("ReactiveMongoAutoSourceController body reader") { request =>
      parse.json(request) mapM {
        case Right(jsValue) =>
          jsValue.validate(reader) map { a =>
            Future.successful(Right(a))
          } recoverTotal { jsError =>
            onJsError(request)(jsError) map Left.apply
          }
        case left_simpleResult =>
          Future.successful(left_simpleResult.asInstanceOf[Either[SimpleResult, A]])
      }
    }

  override def insert =
    insertAction.async(bodyReader(reader)) { request =>
      res.insert(request.body) map { id =>
        Ok(Json.toJson(id))
      }
    }

  override def get(id: BSONObjectID): EssentialAction =
    getAction.async {
      res.get(id) map {
        case None      => NotFound(s"ID ${id.stringify} not found")
        case Some(tid) => Ok(Json.toJson(tid))
      }
    }

  override def delete(id: BSONObjectID): EssentialAction =
    deleteAction.async {
      res.delete(id) map { _ => Ok(Json.toJson(id)) }
    }

  override def update(id: BSONObjectID): EssentialAction =
    updateAction.async(bodyReader(reader)) { request =>
      res.update(id, request.body) map { _ => Ok(Json.toJson(id)) }
    }

  override def updatePartial(id: BSONObjectID): EssentialAction =
    updateAction.async(bodyReader(updateReader)) { request =>
      res.updatePartial(id, request.body) map { _ => Ok(Json.toJson(id)) }
    }

  override def batchInsert: EssentialAction =
    insertAction.async(bodyReader(Reads.seq(reader))) { request =>
      res.batchInsert(Enumerator.enumerate(request.body)) map { nb =>
        Ok(Json.obj("nb" -> nb))
      }
    }

  private def requestParser[A](reader: Reads[A]): BodyParser[A] =
    BodyParser("ReactiveMongoAutoSourceController request parser") { request =>
      request.queryString.get("q") match {
        case None =>
          bodyReader(reader)(request)
        case Some(Seq(str)) =>
          parse.empty(request) mapM { _ =>
            try {
              Json.parse(str).validate(reader) map { a =>
                Future.successful(Right(a))
              } recoverTotal { jsError =>
                onJsError(request)(jsError) map Left.apply
              }
            } catch {
              // catch exceptions from Json.parse
              case ex: java.io.IOException =>
                onBadRequest(request, "Expecting Json value for query parameter 'q'!") map Left.apply
            }
          }
        case Some(seq) =>
          parse.empty(request) mapM { _ =>
            onBadRequest(request, "Expecting single value for query parameter 'q'!") map Left.apply
          }
      }
    }

  private def extractQueryStringInt(request: RequestHeader, param: String): Int =
    request.queryString.get(param) match {
      case Some(Seq(str)) =>
        try { str.toInt } catch { case ex: NumberFormatException => 0 }
      case _ => 0
    }

  override def find: EssentialAction =
    getAction.async(requestParser(queryReader)) { request =>
      val query = request.body
      val limit = extractQueryStringInt(request, "limit")
      val skip  = extractQueryStringInt(request, "skip")

      res.find(query, limit, skip) map { s =>
        Ok(Json.toJson(s))
      }
    }

  override def findStream: EssentialAction =
    getAction.async(requestParser(queryReader)) { request =>
      val query    = request.body
      val skip     = extractQueryStringInt(request, "skip")
      val pageSize = extractQueryStringInt(request, "pageSize")

      Future.successful {
        Ok.chunked(
          res.findStream(query, skip, pageSize)
             .map( it => Json.toJson(it.toTraversable) )
             .andThen(Enumerator.eof)
        )
      }
    }

  override def batchDelete: EssentialAction =
    deleteAction.async(requestParser(queryReader)) { request =>
      val query = request.body
      res.batchDelete(query) map { _ => Ok("deleted") }
    }

  override def batchUpdate: EssentialAction =
    updateAction.async(requestParser(batchReader)) { request =>
      val (q, upd) = request.body
      res.batchUpdate(q, upd) map { _ => Ok("updated") }
    }

}

