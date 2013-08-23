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

import play.autosource.core.AutoSource
import scala.concurrent.{Future, ExecutionContext}
import play.api.libs.iteratee.Enumerator

class SlickAutoSource[T, I] extends AutoSource[T, I, T, T] {
  /** Inserts a record and returns Id
    *
    * @param t the record to be stored
    * @param ctx the execution context used to execute the async action
    * @return the future generated Id (the generated Id can also be part of input data T
              and managed by the underlying implementation).
              if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def insert(t: T)(implicit ctx: ExecutionContext): Future[I] = ???

  /** Fetches a record from its Id
    *
    * @param id the Id of the record to fetch
    * @param ctx the execution context used to execute the async action
    * @return if the id is found, the record is returned plus its Id (because the Id can be part of T but not necessarily).
              if the id is not found, it returns None.
              if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def get(id: I)(implicit ctx: ExecutionContext): Future[Option[(T, I)]] = ???

  /** Deletes a record by its Id
    *
    * @param id the Id of the record to delete
    * @param ctx the execution context used to execute the async action
    * @return if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def delete(id: I)(implicit ctx: ExecutionContext): Future[Unit] = ???

  /** Updates a FULL record by its Id
    *
    * @param id the Id of the record to update
    * @param t the full record data
    * @param ctx the execution context used to execute the async action
    * @return if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def update(id: I, t: T)(implicit ctx: ExecutionContext): Future[Unit] = ???

  /** Updates PARTIALLY a record by its Id
    *
    * @param id the Id of the record to update
    * @param update the partial descriptor of the data to be updated in this record
    * @param ctx the execution context used to execute the async action
    * @return if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def updatePartial(id: I, upd: T)(implicit ctx: ExecutionContext): Future[Unit] = ???

  /** Finds records using a Query selector
    *
    * @param sel the Query selector used to filter returned records
    * @param limit the max number of records to be fetched (could also be integrated in Query selector)
    * @param skip the number of records to skip (offset) before fetching (could also be integrated in Query selector)
    * @param ctx the execution context used to execute the async action
    * @return a sequence of (record plus Id) (the Id can be contained in record but not necessarily)
              if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def find(sel: T, limit: Int, skip: Int)(implicit ctx: ExecutionContext): Future[Seq[(T, I)]] = ???

  /** Finds records using a Query selector and streams results page by page
    * (page streaming can allow better performances than streaming record by reocrd)
    *
    * @param sel the Query selector used to filter returned records
    * @param skip the number of records to skip (offset) before fetching (could also be integrated in Query selector)
    * @param pageSize the max number of records to be fetched by batch (could also be integrated in Query selector)
    * @param ctx the execution context used to execute the async action
    * @return an enumerator of iterator of (record plus Id) (the Id can be contained in record but not necessarily).
              the enumerator represents the stream.
              the iterator represents a page of max size pageSize.
              if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def findStream(sel: T, skip: Int, pageSize: Int)(implicit ctx: ExecutionContext): Enumerator[Iterator[(T, I)]] = ???

  /** Inserts a batch of records and returns the number of inserted records
    *
    * @param t an enumerator of records to be stored (generated Id can be included in T)
    * @param ctx the execution context used to execute the async action
    * @return the future number of inserted records Id.
              if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def batchInsert(elems: Enumerator[T])(implicit ctx: ExecutionContext): Future[Int] = ???

  /** Deletes a batch of records using a query Selector
    *
    * @param sel the Query selector used to filter returned records
    * @param ctx the execution context used to execute the async action
    * @return if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def batchDelete(sel: T)(implicit ctx: ExecutionContext): Future[Unit] = ???

  /** Updates a batch of records using a query Selector and an update Descriptor
    *
    * @param sel the Query selector used to filter returned records
    * @param update the partial descriptor of the data to be updated in this record
    * @param ctx the execution context used to execute the async action
    * @return if there are any DB error, it should be encapsulated in a Future.failed(Exception).
    */
  def batchUpdate(sel: T, upd: T)(implicit ctx: ExecutionContext): Future[Unit] = ???
}
