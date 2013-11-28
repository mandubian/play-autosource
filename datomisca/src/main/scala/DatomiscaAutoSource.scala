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

import play.autosource.core._

import scala.concurrent._

import datomisca._
import datomisca.gen.TypedQuery0

import play.api.Play
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.iteratee.{Enumeratee, Enumerator, Iteratee}

class DatomiscaAutoSource[T](conn: Connection, partition: Partition = Partition.USER)
  ( implicit datomicReader: EntityReader[T],
             datomicWriter: PartialAddEntityWriter[T]
  ) extends AutoSource[T, Long, TypedQuery0[DatomicData], PartialAddEntity, Int] {

  implicit val _conn = conn

  override def insert(t: T)(implicit ctx: ExecutionContext): Future[Long] = {
    val tempid = DId(partition)

    val entity = DatomicMapping.toEntity(tempid)(t)

    for(tx <- Datomic.transact(entity)) yield (tx.resolve(tempid))
  }

  override def get(id: Long)(implicit ctx: ExecutionContext): Future[Option[(T, Long)]] =
    future {
      val entity = conn.database.entity(id)
      if(entity.keySet.isEmpty) None
      else Some(DatomicMapping.fromEntity[T](entity), id)
    }/* recover {
      case e: datomisca.EntityNotFoundException => None
    }*/

  override def delete(id: Long)(implicit ctx: ExecutionContext): Future[Unit] = {
    Datomic.transact(Entity.retract(id)).map( _ => () )
  }

  override def update(id: Long, t: T)(implicit ctx: ExecutionContext): Future[Unit] = {
    val entity = DatomicMapping.toEntity(DId(id))(t)

    Datomic.transact(entity).map( _ => () )
  }

  override def updatePartial(id: Long, upd: PartialAddEntity)(implicit ctx: ExecutionContext): Future[Unit] = {
    Datomic.transact(new AddEntity(DId(id), upd.props)).map{ _ => () }
  }

  val BATCH_SIZE = 100
  val batchEnumeratee: Enumeratee[T, Seq[T]] = 
    Enumeratee.grouped(Enumeratee.take[T](BATCH_SIZE) &>> Iteratee.getChunks)

  override def batchInsert(elems: Enumerator[T])(implicit ctx: ExecutionContext): Future[Int] = {

    (elems &> batchEnumeratee) |>>> Iteratee.foldM(0){ (acc, seq: Seq[T]) => 
      val txData = seq.map{ t =>
        val tempid = DId(partition)
        DatomicMapping.toEntity(tempid)(t)
      }

      Datomic.transact(txData).map{ _ => acc+txData.length }.recover{ case e: Throwable => acc }
    }
  }

  override def find(sel: TypedQuery0[DatomicData], limit: Int = 0, skip: Int = 0)(implicit ctx: ExecutionContext): Future[Iterable[(T, Long)]] =
    future {
      val db = conn.database
      val res = Datomic.q(sel, db) map {
        case DLong(id) =>
          val entity = db.entity(id)
          DatomicMapping.fromEntity[T](entity) -> id
      } drop (if (skip > 0) skip else 0)

      if (limit > 0)
        res.take(limit)
      else
        res
    }

  override def findStream(sel: TypedQuery0[DatomicData], skip: Int = 0, pageSize: Int = 0)(implicit ctx: ExecutionContext): Enumerator[TraversableOnce[(T, Long)]] = {
    val db = conn.database
    val res = Datomic.q(sel, db) map {
      case DLong(id) =>
        val entity = db.entity(id)
        DatomicMapping.fromEntity[T](entity) -> id
    } drop (if (skip > 0) skip else 0)

    if (pageSize > 0)
      Enumerator.enumerate(res.sliding(pageSize))
    else
      Enumerator(res)
  }

  override def batchDelete(sel: TypedQuery0[DatomicData])(implicit ctx: ExecutionContext): Future[Int] = {
    val txData = Datomic.q(sel, conn.database).map{
      case DLong(id) => Entity.retract(id)
    }.toSeq
    Datomic.transact(txData).map( _ => txData.length )
  }

  override def batchUpdate(sel: TypedQuery0[DatomicData], upd: PartialAddEntity)(implicit ctx: ExecutionContext): Future[Int] = {
    val txData = Datomic.q(sel, conn.database).map{
      case DLong(id) => Entity.add(DId(id), upd)
    }.toSeq
    Datomic.transact(txData).map( _ => txData.length )
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

  protected def onJsError(request: RequestHeader)(jsError: JsError): Future[SimpleResult] =
    onBadRequest(request, JsError.toFlatJson(jsError).toString)

  lazy val source = new DatomiscaAutoSource[T](conn, partition)

  private implicit val writerWithId = Writes[(T, Long)] {
    case (t, id) =>
      val ser = implicitly[Writes[T]].writes(t).as[JsObject]
      if((__ \ "id")(ser).isEmpty) ser.as[JsObject] ++ Json.obj("id" -> id)
      else ser
  }
  val idWriter = (__ \ "id").write[Long]

  override def insert =
    insertAction.async(parse.json) { request =>
      Json.fromJson[T](request.body) map { t =>
        source.insert(t) map { id =>
          Ok(Json.toJson(id)(idWriter))
        }
      } recoverTotal onJsError(request)
    }

  override def get(id: Long) =
    getAction.async {
      source.get(id) map {
        case None      => NotFound(s"ID $id not found")
        case Some(tid) => Ok(Json.toJson(tid))
      }
    }

  override def delete(id: Long) =
    deleteAction.async {
      source.delete(id) map { le =>
        Ok(Json.toJson(id)(idWriter))
      }
    }

  override def update(id: Long) =
    updateAction.async(parse.json) { request =>
      Json.fromJson[T](request.body) map { t =>
          source.update(id, t) map { _ =>
            Ok(Json.toJson(id)(idWriter))
          }
      } recoverTotal onJsError(request)
    }

  override def updatePartial(id: Long) =
    updateAction.async(parse.json) { request =>
      Json.fromJson[PartialAddEntity](request.body) map { upd =>
        source.updatePartial(id, upd) map { _ =>
          Ok(Json.toJson(id)(idWriter))
        }
      } recoverTotal onJsError(request)
    }

  override def batchInsert =
    insertAction.async(parse.json) { request =>
      Json.fromJson[Seq[T]](request.body) map { elems =>
        source.batchInsert(Enumerator(elems:_*)) map { nb =>
          Ok(Json.obj("nb" -> nb))
        }
      } recoverTotal onJsError(request)
    }

  private def parseQuery[T](request: Request[T]): Either[String, TypedQuery0[DatomicData]] = {

    request.queryString.get("q") match {
      case None =>
        request.body match {
          case AnyContentAsText(text) => 
            DatomiscaQueryParser.parseTypedQuery0(text)

          case _ => Left("Expected text/plain request body.")
        }

      case Some(Seq(text)) => 
        DatomiscaQueryParser.parseTypedQuery0(text)

      case Some(seq) => Left(s"Bad value for query param 'q': $seq")
    }
  }

  private def parseQueryUpdate[T](request: Request[T]): Either[String, (TypedQuery0[DatomicData], PartialAddEntity)] =
    for {
      q <- (
          request.queryString.get("q") match {
            case None => Left("for streamUpdate, query must in query param 'q'")

            case Some(Seq(text))   => DatomiscaQueryParser.parseTypedQuery0(text)

            case Some(seq) => Left(s"Bad value for query param 'q': $seq")
          }
        ).right
      upd <- (
          request.body match {
            case AnyContentAsJson(json) =>
              Json.fromJson[PartialAddEntity](json) map Right.apply recoverTotal { jsError =>
                Left(s"Request body is not aa valid PartialAddEntity: $jsError.")
              }
            case _ => Left("Request body is not text/json or application/json.")
          }
        ).right
    } yield (q, upd)

  private def extractQueryStringInt(request: RequestHeader, param: String): Int =
    request.queryString.get(param) match {
      case Some(Seq(str)) =>
        try { str.toInt } catch { case ex: NumberFormatException => 0 }
      case _ => 0
    }

  override def find =
    getAction.async { request =>
      parseQuery(request) match {
        case Left(error) =>
          onBadRequest(request, error)
        case Right(query) =>
          val limit = extractQueryStringInt(request, "limit")
          val skip = extractQueryStringInt(request, "skip")

          source.find(query, limit, skip) map { s =>
            Ok(Json.toJson(s))
          }
      }
    }

  override def findStream =
    getAction.async { request =>
      parseQuery(request) match {
        case Left(error) =>
          onBadRequest(request, error)
        case Right(query) =>
          val skip = extractQueryStringInt(request, "skip")
          val pageSize = extractQueryStringInt(request, "pageSize")

          Future.successful {
            Ok.chunked {
              source.findStream(query, skip, pageSize) map { s =>
                Json.toJson(s.toTraversable)
              } andThen (Enumerator.eof)
            }
          }
      }
    }

  override def batchDelete =
    deleteAction.async { request =>
      parseQuery(request) match {
        case Left(error) =>
          onBadRequest(request, error)
        case Right(query) =>
          source.batchDelete(query) map { nb => Ok(Json.obj("nb" -> nb)) }
      }
    }

  override def batchUpdate =
    updateAction.async { request =>
      parseQueryUpdate(request) match {
        case Left(error) =>
          onBadRequest(request, error)
        case Right((q, upd)) =>
          source.batchUpdate(q, upd) map { nb => Ok(Json.obj("nb" -> nb)) }
      }
    }

}

