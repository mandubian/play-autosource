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

import play.api.mvc._
import play.api.libs.json._

class ARequestWithId[Body, Id](
  id:       Id,
  request:  Request[Body]
) extends WrappedRequest[Body](request)

class ARequestWithEntity[Body, A:Format](
  entity:   A,
  request:  Request[Body]
) extends WrappedRequest[Body](request)

class ARequestWithEntityTraversable[Body, A:Format](
  entity:   TraversableOnce[A],
  request:  Request[Body]
) extends WrappedRequest[Body](request)


trait AutoSourceHooks[Id, A] {
  implicit val fmt: Format[A]
  type RequestWithId[Body] = ({type λ[α] = ARequestWithId[α, Id]})#λ[Body]
  type RequestWithEntity[Body] = ({type λ[α] = ARequestWithEntity[α, A]})#λ[Body]
  type RequestWithEntityTraversable[Body] = ({type λ[α] = ARequestWithEntityTraversable[α, A]})#λ[Body]

  def defaultIdHook(id: Id) = new ActionBuilder[RequestWithId] {
    def invokeBlock[A](request: Request[A], block: (RequestWithId[A]) => Future[SimpleResult]) = 
      block(new RequestWithId(id, request))
  }

  def defaultEntityHook(a: A) = new ActionBuilder[RequestWithEntity] {
    def invokeBlock[A](request: Request[A], block: (RequestWithEntity[A]) => Future[SimpleResult]) = 
      block(new RequestWithEntity(a, request))
  }

  def defaultEntityTraversableHook(a: TraversableOnce[A]) = new ActionBuilder[RequestWithEntityTraversable] {
    def invokeBlock[A](request: Request[A], block: (RequestWithEntityTraversable[A]) => Future[SimpleResult]) = 
      block(new RequestWithEntityTraversable(a, request))
  }

  def defaultHook:                            ActionBuilder[Request] = Action
  def getHook(a: A):                          ActionBuilder[Request] = defaultHook
  def insertHook:                             ActionBuilder[Request] = defaultHook
  def updateHook(id: Id):                     ActionBuilder[Request] = defaultHook
  def updatePartialHook(id: Id):              ActionBuilder[Request] = defaultHook
  def deleteHook(id: Id):                     ActionBuilder[Request] = defaultHook
  def findHook(a: TraversableOnce[A]):        ActionBuilder[Request] = defaultHook
  def findStreamHook(a: TraversableOnce[A]):  ActionBuilder[Request] = defaultHook

  def batchInsertHook:            ActionBuilder[Request] = defaultHook
  def batchDeleteHook:            ActionBuilder[Request] = defaultHook
  def batchUpdateHook:            ActionBuilder[Request] = defaultHook
}

abstract class AbstractJsValueAutoSourceController[Id, A : Format]
  extends AutoSourceRouterContoller[Id]
  with    AutoSourceHooks[Id, A] {

  def source: AutoSource[A, Id, JsValue]

  def innerInsert(Request[JsValue]): Future[Result])

  override def insert =
    insertHook.async(parse.json){ request: Request[JsValue] =>
      innerInsert(request)._2
    }

  /*protected def get[A](id: Id) = {
    val a = get[A](id)
    getHook.async(a.parser){request: Request[A] => a(request)}
  }

  protected def deleteAction[A](id: Id) = {
    val a = delete[A](id)
    deleteHook.async(a.parser){request: Request[A] => a(request)}
  }

  protected def updateAction[A](id: Id) = {
    val a = update[A](id)
    updateHook.async(a.parser){request: Request[A] => a(request)}
  }

  protected def updatePartialAction[A](id: Id) = {
    val a = updatePartial[A](id)
    updatePartialHook.async(a.parser){request: Request[A] => a(request)}
  }

  protected def findAction[A] = findHook.async(find.parser){request: Request[A] => find(request)}
  protected def findStreamAction[A] = findStreamHook.async(findStream.parser){request: Request[A] => findStream(request)}

  protected def batchInsertAction[A] = batchInsertHook.async(batchInsert.parser){request: Request[A] => batchInsert(request)}
  protected def batchDeleteAction[A] = batchDeleteHook.async(batchDelete.parser){request: Request[A] => batchDelete(request)}
  protected def batchUpdateAction[A] = batchUpdateHook.async(batchUpdate.parser){request: Request[A] => batchUpdate(request)}
  */
}