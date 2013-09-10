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

import play.api.mvc._
import play.api.Logger

import play.autosource.core.AutoSourceRouterContoller
import slick.dao.{SlickDao, Entity}
import play.api.libs.json._
import scala.Some


abstract class SlickAutoSourceController[E <: Entity[E]:Format] extends AutoSourceRouterContoller[Long] {

  val dao: SlickDao[E]

  val reader: Reads[E] = implicitly[Reads[E]]
  val writer: Writes[E] = implicitly[Writes[E]]
  val idWriter = Writes[Long] { id =>
      Json.obj("id" -> id)
  }

  def insert : EssentialAction = Action(parse.json) { request =>
    Json.fromJson[E](request.body)(reader).map { entity =>
      val persistedEntity: E = {
        Logger.debug("Creating entity in tx: " + entity)
        dao.add(entity)
      }
      Created(writer.writes(persistedEntity))
    }.recoverTotal { e => BadRequest(JsError.toFlatJson(e)) }
  }


   def get(id: Long): EssentialAction = Action { request =>
     val persistedEntityOpt = dao.findOptionById(id)
     persistedEntityOpt match {
       case Some(entity) => Ok(writer.writes(entity))
       case None => entityNotFound(id)
     }
   }


   def delete(id: Long): EssentialAction = Action {
     dao.deleteById(id) match {
       case false => entityNotFound(id)
       case true  => Ok(Json.toJson(id)(idWriter))
     }
   }


  def update(id: Long): EssentialAction = Action(parse.json) { request =>
    Json.fromJson[E](request.body)(reader).map { t =>
      dao.update(t)
      Ok(Json.toJson(id)(idWriter))
    }.recoverTotal { e => BadRequest(JsError.toFlatJson(e)) }
  }

  def updatePartial(id: Long): EssentialAction = update(id)

  def find: EssentialAction = Action { request =>
    val limit = request.queryString.get("limit").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val entities = dao.pagesList(skip, limit)
    Ok(Json.toJson(entities))
  }

  def findStream: EssentialAction = Action  { request =>
    val skip = request.queryString.get("skip").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val pageSize = request.queryString.get("pageSize").flatMap(_.headOption.map(_.toInt)).getOrElse(0)
    val entities = dao.pagesList(skip, pageSize)
    Ok(Json.toJson(entities))
  }

  def batchInsert: EssentialAction = Action(parse.json) { request =>
    Json.fromJson[Seq[E]](request.body)(Reads.seq(reader)).map{ elems =>
      elems.foreach { entity =>
        dao.add(entity)
      }
      Ok(Json.obj("nb" -> elems.size))
    }.recoverTotal{ e => BadRequest(JsError.toFlatJson(e)) }
  }

  def batchDelete: EssentialAction = ???

  def batchUpdate: EssentialAction = ???

  private def entityNotFound(id: Long) = NotFound("No entity found for id:" + id)

 }

