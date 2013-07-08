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
package play.autosource.datomisca

import scala.concurrent._

import datomisca._
import Datomic._
import DatomicMapping._

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.json.syntax._
import play.api.libs.functional.syntax._
import play.api.libs.json.extensions._
import play.api.libs.iteratee._

import play.autosource.core._

object `package` {

}

class DatomiscaAutoSource[T](conn: Connection, partition: Partition = Partition.USER)
  ( implicit datomicReader: EntityReader[T],
             datomicWriter: PartialAddEntityWriter[T]
  ) extends AutoSource[T, Long, TypedQueryAuto0[DatomicData], PartialAddEntity] {

  implicit val theConn = conn

  def insert(t: T)(implicit ctx: ExecutionContext): Future[Long] = {
    val tempid = DId(partition)

    val entity = DatomicMapping.toEntity(tempid)(t)

    for(tx <- Datomic.transact(entity)) yield (tx.resolve(tempid))
  }

  def get(id: Long)(implicit ctx: ExecutionContext): Future[Option[(T, Long)]] = {
    try {
      val entity = database.entity(id)
      future {
        Some(DatomicMapping.fromEntity[T](entity), id)
      }
    } catch{
      case e: datomisca.EntityNotFoundException => Future.successful(None)
      case e: Throwable => Future.failed(e)
    }

  }

  def delete(id: Long)(implicit ctx: ExecutionContext): Future[Unit] = {
    Datomic.transact(Entity.retract(id)).map( _ => () )
  }

  def update(id: Long, t: T)(implicit ctx: ExecutionContext): Future[Unit] = {
    val entity = DatomicMapping.toEntity(DId(id))(t)

    Datomic.transact(entity).map( _ => () )
  }

  def updatePartial(id: Long, upd: PartialAddEntity)(implicit ctx: ExecutionContext): Future[Unit] = {
    Datomic.transact(AddEntity(DId(id), upd)).map{ _ => () }
  }

  val BATCH_SIZE = 100
  val batchEnumeratee: Enumeratee[T, Seq[T]] = 
    Enumeratee.grouped(Enumeratee.take[T](BATCH_SIZE) &>> Iteratee.getChunks)

  def batchInsert(elems: Enumerator[T])(implicit ctx: ExecutionContext): Future[Int] = {

    (elems &> batchEnumeratee) |>>> Iteratee.foldM(0){ (acc, seq: Seq[T]) => 
      val txData = seq.map{ t =>
        val tempid = DId(partition)
        DatomicMapping.toEntity(tempid)(t)
      }

      Datomic.transact(txData).map{ _ => acc+txData.length }.recover{ case e: Throwable => acc }
    }
  }

  def find(sel: TypedQueryAuto0[DatomicData], limit: Int = 0, skip: Int = 0)(implicit ctx: ExecutionContext): Future[Seq[(T, Long)]] = {
    future {
      var res = Datomic.q(sel, database).map{
        case DLong(id) =>
          val entity = database.entity(id)
          DatomicMapping.fromEntity[T](entity) -> id
      }
      if(skip!=0) res = res.drop(skip)
      if(limit!=0) res = res.take(limit)
      res
    }
  }

  def findStream(sel: TypedQueryAuto0[DatomicData], skip: Int = 0, pageSize: Int = 0)(implicit ctx: ExecutionContext): Enumerator[Iterator[(T, Long)]] = {
    var res = Datomic.q(sel, database).map{
      case DLong(id) =>
        val entity = database.entity(id)
        DatomicMapping.fromEntity[T](entity) -> id
    }
    if(skip!=0) res = res.drop(skip)
    if(pageSize!=0) Enumerator.enumerate(res.sliding(pageSize).map(_.toIterator))
    else Enumerator.enumerate(Seq(res.toIterator))
  }

  def batchDelete(sel: TypedQueryAuto0[DatomicData])(implicit ctx: ExecutionContext): Future[Unit] = {
    val txData = Datomic.q(sel, database).map{
      case DLong(id) => Entity.retract(id)
    }
    Datomic.transact(txData).map( _ => ())
  }

  def batchUpdate(sel: TypedQueryAuto0[DatomicData], upd: PartialAddEntity)(implicit ctx: ExecutionContext): Future[Unit] = {
    val txData = Datomic.q(sel, database).map{
      case DLong(id) => Entity.add(DId(id), upd)
    }
    Datomic.transact(txData).map( _ => ())
  }

}

abstract class DatomiscaAutoSourceController[T]
  ( implicit datomicReader: EntityReader[T],
             datomicWriter: PartialAddEntityWriter[T],
             format: Format[T],
             updReader: Reads[PartialAddEntity],
             ctx: ExecutionContext
  ) extends AutoSourceRouterContoller[Long]
  {

  val conn: Connection
  val partition: Partition

  lazy val source = new DatomiscaAutoSource[T](conn, partition)

  val reader: Reads[T] = implicitly[Reads[T]]
  val updateReader: Reads[PartialAddEntity] = implicitly[Reads[PartialAddEntity]]
  /*val queryReader: Reads[JsObject] = implicitly[Reads[JsObject]]
  val updateReader: Reads[JsObject] = implicitly[Reads[JsObject]]
  val batchReader: Reads[(JsObject, JsObject)] = (
    (__ \ "query").read(queryReader) and
    (__ \ "update").read(updateReader)
  ).tupled

  val limitReader: Reads[Int] = (__ \ "limit").read[Int]
  val pageSizeReader: Reads[Int] = (__ \ "pageSize").read[Int]
  val skipReader: Reads[Int] = (__ \ "skip").read[Int]
  */

  val writer: Writes[T] = implicitly[Writes[T]]
  val writerWithId = Writes[(T, Long)] {
    case (t, id) =>
      val ser = writer.writes(t).as[JsObject]
      if((__ \ "id")(ser).isEmpty) ser.as[JsObject] ++ Json.obj("id" -> id)
      else ser
  }
  val idWriter = (__ \ "id").write[Long]

  def insert: EssentialAction = Action(parse.json){ request =>
    Json.fromJson[T](request.body)(reader).map{ t =>
      Async{
        source.insert(t).map{ id => Ok(Json.toJson(id)(idWriter)) }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def get(id: Long): EssentialAction = Action{
    Async{
      source.get(id).map{
        case None    => NotFound(s"ID $id not found")
        case Some(tid) => Ok(Json.toJson(tid)(writerWithId))
      }
    }
  }

  def delete(id: Long): EssentialAction = Action{
    Async{
      source.delete(id).map{ le => Ok(Json.toJson(id)(idWriter)) }
    }
  }

  def update(id: Long): EssentialAction = Action(parse.json){ request =>
    Json.fromJson[T](request.body)(reader).map{ t =>
      Async{
        source.update(id, t).map{ _ => Ok(Json.toJson(id)(idWriter)) }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def updatePartial(id: Long): EssentialAction = Action(parse.json){ request =>
    Json.fromJson[PartialAddEntity](request.body)(updateReader).map{ upd =>
      Async{
        source.updatePartial(id, upd).map{ _ => Ok(Json.toJson(id)(idWriter)) }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def batchInsert: EssentialAction = Action(parse.json){ request =>
    Json.fromJson[Seq[T]](request.body)(Reads.seq(reader)).map{ elems =>
      Async{
        source.batchInsert(Enumerator(elems:_*)).map{ nb => Ok(Json.obj("nb" -> nb)) }
      }
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  private def parseQuery[T](request: Request[T]): TypedQueryAuto0[DatomicData] = {
    request.queryString.get("q") match {
      case None =>
        request.body match {
          case AnyContentAsText(text) => 
            DatomicParser.parseQuerySafe(text) match {
              case Left(failure) => throw new RuntimeException(s"Body in Request isn't TypedQueryAuto0 failure($failure)")
              case Right(pureQuery) =>
                if(pureQuery.in.isDefined && pureQuery.in.get.inputs.length > 0)
                  throw new RuntimeException("Body in Request isn't TypedQueryAuto0: can't accept input params")

                if(pureQuery.find.outputs.length > 1)
                  throw new RuntimeException("Body in Request isn't TypedQueryAuto0: can't accept more than 1 output param")

                TypedQueryAuto0[DatomicData](pureQuery)
            }
          case _ => throw new RuntimeException("Body in Request isn't TypedQueryAuto0")
        }

      case Some(q)   =>
        q.headOption.map{ text =>
          DatomicParser.parseQuerySafe(text) match {
            case Left(failure) => throw new RuntimeException(s"Body in Request isn't TypedQueryAuto0 failure($failure)")
            case Right(pureQuery) =>
              if(pureQuery.in.isDefined && pureQuery.in.get.inputs.length > 0)
                throw new RuntimeException("Body in Request isn't TypedQueryAuto0: can't accept input params")

              if(pureQuery.find.outputs.length > 1)
                throw new RuntimeException("Body in Request isn't TypedQueryAuto0: can't accept more than 1 output param")

              TypedQueryAuto0[DatomicData](pureQuery)
          }
        }.get
    }
  }

  private def parseQueryUpdate[T](request: Request[T]): (TypedQueryAuto0[DatomicData], PartialAddEntity) = {
    val q = request.queryString.get("q") match {
      case None => throw new RuntimeException("for streamUpdate, query must in query param 'q'")

      case Some(q)   =>
        q.headOption.map{ text =>
          DatomicParser.parseQuerySafe(text) match {
            case Left(failure) => throw new RuntimeException(s"Body in Request isn't TypedQueryAuto0 failure($failure)")
            case Right(pureQuery) =>
              if(pureQuery.in.isDefined && pureQuery.in.get.inputs.length > 0)
                throw new RuntimeException("Body in Request isn't TypedQueryAuto0: can't accept input params")

              if(pureQuery.find.outputs.length > 1)
                throw new RuntimeException("Body in Request isn't TypedQueryAuto0: can't accept more than 1 output param")

              TypedQueryAuto0[DatomicData](pureQuery)
          }
        }.get
    }

    val upd = request.body match {
      case AnyContentAsJson(json) => 
        Json.fromJson[PartialAddEntity](json)(updateReader)
            .recoverTotal{ e => 
              throw new RuntimeException(
                "couldn't parse update descriptor as Json from request body error:"+e
              ) 
            }

      case _ => throw new RuntimeException("update descriptor in Body Request isn't Json")
    }

    (q, upd)
  }  

  def find: EssentialAction = Action{ request =>
    val query: TypedQueryAuto0[DatomicData] = parseQuery(request)
    val limit = request.queryString.get("limit").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)

    Async{
      source.find(query, limit, skip).map{ s =>
        Ok(Json.toJson(s)(Writes.seq(writerWithId)))
      }
    }
  }

  def findStream: EssentialAction = Action { request =>
    val query: TypedQueryAuto0[DatomicData] = parseQuery(request)
    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val pageSize = request.queryString.get("pageSize").flatMap(_.headOption.map(_.toInt)).getOrElse(0)

    Ok.stream(
      source.findStream(query, skip, pageSize)
            .map{ s => Json.toJson(s.toSeq)(Writes.seq(writerWithId)) }
            .andThen(Enumerator.eof)
    )
  }

  def batchDelete: EssentialAction = Action{ request =>
    val query: TypedQueryAuto0[DatomicData] = parseQuery(request)

    Async {
      source.batchDelete(query).map{ _ => Ok("deleted") }
    }
  }

  def batchUpdate: EssentialAction = Action{ request =>
    val (q, upd) = parseQueryUpdate(request)

    Async{
      source.batchUpdate(q, upd).map{ _ => Ok("updated") }
    }
  }

}

