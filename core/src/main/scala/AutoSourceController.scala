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
package play.autosource.core

import scala.concurrent._

import play.api.Play
import play.api.mvc._
import play.core.Router
import play.api.libs.iteratee.Enumerator

/**
  * AutoSource controller defining all basic CRUD actions
  * The only parameterized type is the Id of records
  */
trait AutoSourceController[Id] extends Controller {

  /**
    */
  def insert: EssentialAction

  def get(id: Id): EssentialAction
  def delete(id: Id): EssentialAction
  def update(id: Id): EssentialAction
  def updatePartial(id: Id): EssentialAction

  def find: EssentialAction
  def findStream: EssentialAction

  def batchInsert: EssentialAction
  def batchDelete: EssentialAction
  def batchUpdate: EssentialAction


  protected def defaultAction: ActionBuilder[Request] = Action
  protected def getAction:     ActionBuilder[Request] = defaultAction
  protected def insertAction:  ActionBuilder[Request] = defaultAction
  protected def updateAction:  ActionBuilder[Request] = defaultAction
  protected def deleteAction:  ActionBuilder[Request] = defaultAction

  protected def onBadRequest(request: RequestHeader, error: String): Future[SimpleResult] =
    Play.maybeApplication map { app =>
      app.global.onBadRequest(request, error)
    } getOrElse {
      Future.successful(BadRequest)
    }
}

/**
  * Directly inspired (not to say copied ;))
  * from James Roper's "Advanced routing in Play Framework" article
  * http://jazzy.id.au/default/2013/05/08/advanced_routing_in_play_framework.html
  */
abstract class AutoSourceRouterContoller[Id](implicit idBindable: PathBindable[Id])
  extends Router.Routes
  with AutoSourceController[Id] {

  private var path: String = ""

  private val Slash        = "/?".r
  private val Id           = "/([^/]+)/?".r
  private val Partial      = "/([^/]+)/partial".r
  private val Find         = "/find/?".r
  private val Batch        = "/batch/?".r
  private val Stream       = "/stream/?".r

  def withId(id: String, action: Id => EssentialAction) =
    idBindable.bind("id", id).fold(badRequest, action)

  def setPrefix(prefix: String) {
    path = prefix
  }

  def prefix = path
  def documentation = Nil
  def routes = new scala.runtime.AbstractPartialFunction[RequestHeader, Handler] {
    override def applyOrElse[A <: RequestHeader, B >: Handler](rh: A, default: A => B) = {
      if (rh.path.startsWith(path)) {
        (rh.method, rh.path.drop(path.length)) match {
          case ("GET",    Stream())    => findStream
          case ("GET",    Id(id))      => withId(id, get)
          case ("GET",    Slash())     => find

          case ("PUT",    Batch())     => batchUpdate
          case ("PUT",    Partial(id)) => withId(id, updatePartial)
          case ("PUT",    Id(id))      => withId(id, update)

          case ("POST",   Batch())     => batchInsert
          case ("POST",   Find())      => find
          case ("POST",   Slash())     => insert

          case ("DELETE", Batch())     => batchDelete
          case ("DELETE", Id(id))      => withId(id, delete)
          case _                       => default(rh)
        }
      } else {
        default(rh)
      }
    }

    def isDefinedAt(rh: RequestHeader) =
      if (rh.path.startsWith(path)) {
        (rh.method, rh.path.drop(path.length)) match {
          case ("GET",    Stream()   | Id(_)      | Slash()) => true
          case ("PUT",    Batch()    | Partial(_) | Id(_))   => true
          case ("POST",   Batch()    | Slash())              => true
          case ("DELETE", Batch()    | Id(_))                => true
          case _ => false
        }
      } else {
        false
      }
  }
}
