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
import play.api.libs.iteratee.Enumerator

trait AutoSource[T, Id, Query, Update] {
  def insert(t: T)(implicit ctx: ExecutionContext): Future[Id]

  def get(id: Id)(implicit ctx: ExecutionContext): Future[Option[(T, Id)]]

  def delete(id: Id)(implicit ctx: ExecutionContext): Future[Unit]

  def update(id: Id, t: T)(implicit ctx: ExecutionContext): Future[Unit]
  def updatePartial(id: Id, upd: Update)(implicit ctx: ExecutionContext): Future[Unit]

  def find(sel: Query, limit: Int = 0, skip: Int = 0)(implicit ctx: ExecutionContext): Future[Seq[(T, Id)]]
  def findStream(sel: Query, skip: Int = 0, pageSize: Int = 0)(implicit ctx: ExecutionContext): Enumerator[Iterator[(T, Id)]]

  def batchInsert(elems: Enumerator[T])(implicit ctx: ExecutionContext): Future[Int]
  def batchDelete(sel: Query)(implicit ctx: ExecutionContext): Future[Unit]
  def batchUpdate(sel: Query, upd: Update)(implicit ctx: ExecutionContext): Future[Unit]
}
