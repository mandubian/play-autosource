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

trait AutoSourceHooks[Id] {

  def defaultHook:        ActionBuilder[Request] = Action
  def getHook:            ActionBuilder[Request] = defaultHook
  def insertHook:         ActionBuilder[Request] = defaultHook
  def updateHook:         ActionBuilder[Request] = defaultHook
  def updatePartialHook:  ActionBuilder[Request] = defaultHook
  def deleteHook:         ActionBuilder[Request] = defaultHook
  def findHook:           ActionBuilder[Request] = defaultHook
  def findStreamHook:     ActionBuilder[Request] = defaultHook

  def batchInsertHook:    ActionBuilder[Request] = defaultHook
  def batchDeleteHook:    ActionBuilder[Request] = defaultHook
  def batchUpdateHook:    ActionBuilder[Request] = defaultHook
}

abstract class AbstractJsValueAutoSourceController[Id : PathBindable, A : Format]
  extends AutoSourceRouterContoller[Id]
  with    AutoSourceHooks[Id] {

  def insertBlock: Request[JsValue] => Future[SimpleResult]
  override def insert: EssentialAction = insertHook.async(parse.json)(insertBlock)

  def getBlock(id: Id): Request[AnyContent] => Future[SimpleResult]
  override def get(id: Id): EssentialAction = getHook.async(getBlock(id))

  def deleteBlock(id: Id): Request[AnyContent] => Future[SimpleResult]
  override def delete(id: Id): EssentialAction = deleteHook.async(deleteBlock(id))

  def updateBlock(id: Id): Request[JsValue] => Future[SimpleResult]
  override def update(id: Id): EssentialAction = updateHook.async(parse.json)(updateBlock(id))

  def updatePartialBlock(id: Id): Request[JsValue] => Future[SimpleResult]
  override def updatePartial(id: Id): EssentialAction = updatePartialHook.async(parse.json)(updatePartialBlock(id))

  def findBlock: Request[AnyContent] => Future[SimpleResult]
  override def find: EssentialAction = findHook.async(findBlock)

  def findStreamBlock: Request[AnyContent] => Future[SimpleResult]
  override def findStream: EssentialAction = findStreamHook.async(findStreamBlock)

  def batchInsertBlock: Request[JsValue] => Future[SimpleResult]
  override def batchInsert: EssentialAction = batchInsertHook.async(parse.json)(batchInsertBlock)

  def batchDeleteBlock: Request[AnyContent] => Future[SimpleResult]
  override def batchDelete: EssentialAction = batchDeleteHook.async(batchDeleteBlock)

  def batchUpdateBlock: Request[JsValue] => Future[SimpleResult]
  override def batchUpdate: EssentialAction = batchUpdateHook.async(parse.json)(batchUpdateBlock)

}