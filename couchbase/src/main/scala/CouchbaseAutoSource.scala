/**
  * Copyright 2013 Mathieu ANCELIN (@TrevorReznik)
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
package play.autosource.couchbase

import java.util.UUID

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.ancelin.play2.couchbase.CouchbaseBucket
import org.ancelin.play2.couchbase.CouchbaseRWImplicits
import org.ancelin.play2.couchbase.crud.QueryObject

import com.couchbase.client.protocol.views.Query
import com.couchbase.client.protocol.views.View

import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Format
import play.api.libs.json.JsError
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsUndefined
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.Reads
import play.api.libs.json.Writes
import play.api.mvc.Action
import play.api.mvc.EssentialAction
import play.autosource.core.AutoSource
import play.autosource.core.AutoSourceRouterContoller

class CouchbaseAutoSource[T:Format](bucket: CouchbaseBucket, idKey: String = "_id") extends AutoSource[T, String, (View, Query), JsObject] {

  val reader: Reads[T] = implicitly[Reads[T]]
  val writer: Writes[T] = implicitly[Writes[T]]

  def insert(t: T)(implicit ctx: ExecutionContext): Future[String] = {
    val id: String = UUID.randomUUID().toString
    val json = writer.writes(t).as[JsObject]
    json \ idKey match {
      case _:JsUndefined => {
        val newJson = json ++ Json.obj(idKey -> JsString(id))
        bucket.set(id, newJson)(CouchbaseRWImplicits.jsObjectToDocumentWriter, ctx).map(_ => id)(ctx)
      }
      case actualId: JsString => {
        bucket.set(actualId.value, json)(CouchbaseRWImplicits.jsObjectToDocumentWriter, ctx).map(_ => actualId.value)(ctx)
      }
      case _ => throw new RuntimeException(s"Field with $idKey already exists and not of type JsString")
    }
  }

  def get(id: String)(implicit ctx: ExecutionContext): Future[Option[(T, String)]] = {
    bucket.get[T]( id )(reader, ctx).map( _.map( v => ( v, id ) ) )(ctx)
  }

  def delete(id: String)(implicit ctx: ExecutionContext): Future[Unit] = {
    bucket.delete(id)(ctx).map(_ => ())
  }

  def update(id: String, t: T)(implicit ctx: ExecutionContext): Future[Unit] = {
    bucket.replace(id, t)(writer, ctx).map(_ => ())
  }

  def updatePartial(id: String, upd: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    get(id)(ctx).flatMap { opt =>
      opt.map { t =>
        val json = Json.toJson(t._1)(writer).as[JsObject]
        val newJson = json.deepMerge(upd)
        bucket.replace((json \ idKey).as[JsString].value, newJson)(CouchbaseRWImplicits.jsObjectToDocumentWriter, ctx).map(_ => ())
      }.getOrElse(throw new RuntimeException(s"Cannot find ID $id"))
    }
  }

  def batchInsert(elems: Enumerator[T])(implicit ctx: ExecutionContext): Future[Int] = {
    elems(Iteratee.foldM[T, Int](0)( (s, t) => insert(t)(ctx).map(_ => s + 1))).flatMap(_.run)
  }

  def find(sel: (View, Query), limit: Int = 0, skip: Int = 0)(implicit ctx: ExecutionContext): Future[Seq[(T, String)]] = {
    var query = sel._2
    if (limit != 0) query = query.setLimit(limit)
    if (skip != 0) query = query.setSkip(skip)
    bucket.search[JsObject](sel._1)(query)(CouchbaseRWImplicits.documentAsJsObjectReader, ctx).toList(ctx).map{ l =>
      l.map { i => 
        val t = reader.reads(i.document) match {
          case e:JsError => throw new RuntimeException("Document does not match object")
          case s:JsSuccess[T] => s.get
        }
        i.document \ idKey match {
          case actualId: JsString => (t, actualId.value)
          case _ => (t, i.id.get)
        }
      }
    }
  }

  def findStream(sel: (View, Query), skip: Int = 0, pageSize: Int = 0)(implicit ctx: ExecutionContext): Enumerator[TraversableOnce[(T, String)]] = {
    var query = sel._2
    if (skip != 0) query = query.setSkip(skip)
    val futureEnumerator:Future[Enumerator[TraversableOnce[(T, 
 String)]]] = bucket.search[JsObject](sel._1)(query)(CouchbaseRWImplicits.documentAsJsObjectReader, ctx).toList(ctx).map { l =>
      val size = if(pageSize != 0) pageSize else l.size
      Enumerator.enumerate(l.map { i => 
          val t = reader.reads(i.document) match {
            case e:JsError => throw new RuntimeException("Document does not match object")
            case s:JsSuccess[T] => s.get
          }
          i.document \ idKey match {
            case actualId: JsString => (t, actualId.value)
            case _ => (t, i.id.get)
          }
        }.grouped(size).map(_.toList))
    }
   Enumerator.flatten(futureEnumerator)
  }

  def batchDelete(sel: (View, Query))(implicit ctx: ExecutionContext): Future[Unit] = {
    bucket.search[JsObject](sel._1)(sel._2)(CouchbaseRWImplicits.documentAsJsObjectReader, ctx).toList(ctx).map { list =>
      list.map { t =>
        delete(t.id.get)(ctx)
      }
    }
  }

  def batchUpdate(sel: (View, Query), upd: JsObject)(implicit ctx: ExecutionContext): Future[Unit] = {
    bucket.search[T](sel._1)(sel._2)(reader, ctx).toList(ctx).map { list =>
      list.map { t =>
        val json = Json.toJson(t.document)(writer).as[JsObject]
        val newJson = json.deepMerge(upd)
        bucket.replace(t.id.get, newJson)(CouchbaseRWImplicits.jsObjectToDocumentWriter, ctx).map(_ => ())
      }
    }
  }

  def view(docName: String, viewName: String)(implicit ctx: ExecutionContext): Future[View] = {
    bucket.view(docName, viewName)(ctx)
  }
}

abstract class CouchbaseAutoSourceController[T:Format](implicit ctx: ExecutionContext) extends AutoSourceRouterContoller[String] {

  def bucket: CouchbaseBucket
  def defaultDesignDocname: String
  def defaultViewName: String
  def idKey: String = "_id"

  lazy val res = new CouchbaseAutoSource[T](bucket, idKey)

  val writerWithId = Writes[(T, String)] {
    case (t, id) => {
      val jsObj = res.writer.writes(t).as[JsObject]
      (jsObj \ idKey) match {
        case _:JsUndefined => jsObj ++ Json.obj(idKey -> id)
        case actualId => jsObj
      }
    }
  }

  def insert: EssentialAction = Action.async(parse.json) { request =>
    Json.fromJson[T](request.body)(res.reader).map{ t =>
      res.insert(t).map{ id => Ok(Json.obj("id" -> id)) }
    }.recoverTotal{ e => Future(BadRequest(JsError.toFlatJson(e))) }
  }

  def get(id: String): EssentialAction = Action.async {
    res.get(id).map{
      case None    => NotFound(s"ID '${id}' not found")
      case Some(tid) => {
        val jsObj = Json.toJson(tid._1)(res.writer).as[JsObject]
        (jsObj \ idKey) match {
          case _:JsUndefined => Ok( jsObj ++ Json.obj(idKey -> JsString(id)) )
          case actualId => Ok( jsObj )
        }
      }
    }
  }

  def delete(id: String): EssentialAction = Action.async {
    res.delete(id).map{ le => Ok(Json.obj("id" -> id)) }
  }

  def update(id: String): EssentialAction = Action.async(parse.json) { request =>
    Json.fromJson[T](request.body)(res.reader).map{ t =>
      res.update(id, t).map{ _ => Ok(Json.obj("id" -> id)) }
    }.recoverTotal{ e => Future(BadRequest(JsError.toFlatJson(e))) }
  }

  def find: EssentialAction = Action.async { request =>
    val (queryObject, query) = QueryObject.extractQuery(request, defaultDesignDocname, defaultViewName)
    res.view(queryObject.docName, queryObject.view).flatMap { view =>
      res.find((view, query))
    }.map( s => Ok(Json.toJson(s)(Writes.seq(writerWithId))))
  }

  def findStream: EssentialAction = Action.async { request =>
    val (queryObject, query) = QueryObject.extractQuery(request, defaultDesignDocname, defaultViewName)
    res.view(queryObject.docName, queryObject.view).map { view =>
      res.findStream((view, query), 0, 0)
    }.map { s => Ok.stream(
      s.map( it => Json.toJson(it.toSeq)(Writes.seq(writerWithId)) ).andThen(Enumerator.eof) )
    }
  }

  def updatePartial(id: String): EssentialAction = Action.async(parse.json) { request =>
    Json.fromJson[JsObject](request.body)(CouchbaseRWImplicits.documentAsJsObjectReader).map{ upd =>
      res.updatePartial(id, upd).map{ _ => Ok(Json.obj("id" -> id)) }
    }.recoverTotal{ e => Future(BadRequest(JsError.toFlatJson(e))) }
  }

  def batchInsert: EssentialAction = Action.async(parse.json) { request =>
    Json.fromJson[Seq[T]](request.body)(Reads.seq(res.reader)).map{ elems =>
      res.batchInsert(Enumerator(elems:_*)).map{ nb => Ok(Json.obj("nb" -> nb)) }
    }.recoverTotal{ e => Future(BadRequest(JsError.toFlatJson(e))) }
  }

  def batchDelete: EssentialAction = Action.async { request =>
    val (queryObject, query) = QueryObject.extractQuery(request, defaultDesignDocname, defaultViewName)
    res.view(queryObject.docName, queryObject.view).flatMap { view =>
      res.batchDelete((view, query)).map{ _ => Ok("deleted") }
    }
  }

  def batchUpdate: EssentialAction = Action.async(parse.json) { request =>
    val (queryObject, query) = QueryObject.extractQuery(request, defaultDesignDocname, defaultViewName)
    Json.fromJson[JsObject](request.body)(CouchbaseRWImplicits.documentAsJsObjectReader).map{ upd =>
      res.view(queryObject.docName, queryObject.view).flatMap { view =>
        res.batchUpdate((view, query), upd).map{ _ => Ok("updated") }
      }
    }.recoverTotal{ e => Future(BadRequest(JsError.toFlatJson(e))) }
  }
}




