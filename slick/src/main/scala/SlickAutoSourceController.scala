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

import play.autosource.core.{AutoSourceRouterContoller, AutoSourceController}
import slick.dao.{Entity,GenericDao}
import play.api.libs.json.{Json, OFormat, JsValue}

 abstract class SlickAutoSourceController[E <: Entity[E]] extends AutoSourceRouterContoller[Long] {

  val dao: GenericDao[E]
  val mapper : Mapper[E]

  def insert : EssentialAction = Action {
    request =>
      request.body.asJson match {
        case Some(json) => createEntityFromJson(json)
        case None => BadRequest("Empty body! Can't create entity.")
      }
  }

  private def createEntityFromJson(jsValue: JsValue) = {
    Logger.debug("Payload: " + jsValue)
    val entity: E = mapper.fromJson(jsValue)

    val persistedEntity: E = {
      Logger.debug("Creating entity in tx: " + entity)
      dao.add(entity)
    }

    Logger.debug("Persisted entity: " + persistedEntity)
    Created(mapper.toJson(persistedEntity))
  }


   def get(id: Long): EssentialAction = Action { request =>

     val persistedEntityOpt = dao.findOptionById(id)

     persistedEntityOpt match {
       case Some(entity) => Ok(mapper.toJson(entity))
       case None => entityNotFound(id)
     }
   }


   def delete(id: Long): EssentialAction = Action {
     dao.deleteById(id) match {
       case false => entityNotFound(id)
       case true  => Ok("Delete for id: " + id)
     }
   }


  def update(id: Long): EssentialAction = Action { request =>
    request.body.asJson match {
      case Some(json) => {
        try {
          dao.update(mapper.fromJson(json))
          Ok(json)
        } catch {
          case e:Exception => BadRequest(e.getMessage)
        }
      }
      case None => BadRequest("Empty body! Can't update entity.")
    }
  }

  def updatePartial(id: Long): EssentialAction = ???

  def find: EssentialAction = Action {
    val entities = dao.list()
    Ok(mapper.toJson(entities))
  }

  def findStream: EssentialAction = ???

  def batchInsert: EssentialAction = ???

  def batchDelete: EssentialAction = ???

  def batchUpdate: EssentialAction = ???

  private def entityNotFound(id: Long) = NotFound("No entity found for id:" + id)

 }

trait Mapper[E <: Entity[E]] {
  implicit val format : OFormat[E]
  def toJson(seq: Seq[E]) : JsValue = Json.toJson(seq)
  def toJson(entity: E) : JsValue = format.writes(entity)
  def fromJson(jsValue: JsValue) = format.reads(jsValue).get
}
