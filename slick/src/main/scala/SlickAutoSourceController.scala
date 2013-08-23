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

import play.autosource.core.AutoSourceController

object SlickAutoSourceController extends  AutoSourceController[Long] {
  
  def insert: EssentialAction = ???

  def get(id: Long): EssentialAction = ???

  def delete(id: Long): EssentialAction = ???

  def update(id: Long): EssentialAction = ???

  def updatePartial(id: Long): EssentialAction = ???

  def find: EssentialAction = ???

  def findStream: EssentialAction = ???

  def batchInsert: EssentialAction = ???

  def batchDelete: EssentialAction = ???

  def batchUpdate: EssentialAction = ???
}
