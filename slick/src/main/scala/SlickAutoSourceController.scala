/**
 * Copyright 2013 Renato Guerra Cavalcanti (@renatocaval)
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

package play.autosource.slick

import play.api.Play.current
import play.api.mvc._
import play.api.Logger

import play.autosource.core.AutoSourceRouterContoller
import slick.dao.{SlickDao, Entity}
import play.api.libs.json._
import play.api.db.slick._

abstract class SlickAutoSourceController[E <: Entity[E]:Format:SlickDao] extends AutoSourceRouterContoller[Long] {

  def dao: SlickDao[E] = implicitly[SlickDao[E]]

  val reader: Reads[E] = implicitly[Reads[E]]
  val writer: Writes[E] = implicitly[Writes[E]]

  val idWriter = Writes[Long] { id => Json.obj("id" -> id) }
  val idReader: Reads[Long] = (__ \ "id").read[Long]

  def insert : EssentialAction = DBAction { request =>
    parseRequestWithSession(request) { jsValue =>
      Json.fromJson[E](jsValue)(reader).map { entity =>
        val persistedEntity: E = {
          Logger.debug("Creating entity in tx: " + entity)
          dao.add(entity)(request.dbSession)
        }
        Created(writer.writes(persistedEntity))
      }.recoverTotal { e => BadRequest(JsError.toFlatJson(e)) }
    }
  }


   def get(id: Long): EssentialAction = DBAction { request =>
     val persistedEntityOpt = dao.findOptionById(id)(request.dbSession)
     persistedEntityOpt match {
       case Some(entity) => Ok(writer.writes(entity))
       case None => entityNotFound(id)
     }
   }


   def delete(id: Long): EssentialAction = DBAction { request =>
     dao.deleteById(id)(request.dbSession) match {
       case false => entityNotFound(id)
       case true  => Ok(Json.toJson(id)(idWriter))
     }
   }


  def update(id: Long): EssentialAction = DBAction { request =>
    parseRequestWithSession(request) { jsValue =>
      Json.fromJson[E](jsValue)(reader).map { t =>
        dao.update(t)(request.dbSession)
        Ok(Json.toJson(id)(idWriter))
      }.recoverTotal { e => BadRequest(JsError.toFlatJson(e)) }
    }
  }


  def updatePartial(id: Long): EssentialAction = update(id)

  def find: EssentialAction = DBAction { requestWithSession =>
    val request = requestWithSession.request

    val limit = request.queryString.get("limit").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)

    val entities = dao.pagesList(skip, limit)(requestWithSession.dbSession)
    Ok(Json.toJson(entities))
  }

  def findStream: EssentialAction = DBAction { requestWithSession =>
    val request = requestWithSession.request

    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val pageSize = request.queryString.get("pageSize").flatMap(_.headOption.map(_.toInt)).getOrElse(0)

    val entities = dao.pagesList(skip, pageSize)(requestWithSession.dbSession)
    Ok(Json.toJson(entities))
  }

  def batchInsert: EssentialAction = DBAction { request =>

    parseRequestWithSession(request) { jsValue =>
      Json.fromJson[Seq[E]](jsValue)(Reads.seq(reader)).map{ elems =>
        elems.foreach { entity =>
          dao.add(entity)(request.dbSession)
        }
        Ok(Json.obj("nb" -> elems.size))
      }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
    }
  }

  def batchDelete: EssentialAction = DBAction { request =>

    parseRequestWithSession(request) { jsValue =>
      Json.fromJson[Seq[Long]](jsValue)(Reads.seq(idReader)).map { ids =>
        ids.foreach(id => dao.deleteById(id)(request.dbSession))
        Ok(Json.obj("nb" -> ids.size))
      }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
    }
  }

  def batchUpdate: EssentialAction = DBAction { request =>

    parseRequestWithSession(request) { jsValue =>
      Json.fromJson[Seq[E]](jsValue)(Reads.seq(reader)).map{ elems =>
        elems.foreach { entity =>
          dao.update(entity)(request.dbSession)
        }
        Ok(Json.obj("nb" -> elems.size))
      }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
    }
  }


  private def entityNotFound(id: Long) = NotFound("No entity found for id:" + id)

  private def parseRequestWithSession[A](requestWithDbSession: DBSessionRequest[A])(block:(JsValue) => SimpleResult) : SimpleResult = {
    val jsValue = requestWithDbSession.request.body match {
          case AnyContentAsJson(json) => json
          case AnyContentAsEmpty => Json.obj()
          case _ => throw new RuntimeException("Body in Request isn't Json")
    }
    block(jsValue)
  }

 }
