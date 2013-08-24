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
package slick.dao

import play.api.db.slick.DB
import play.api.Play.current
import scala.reflect.ClassTag
import scala.language.implicitConversions

class CannotUpdateNonPersistedEntityException(entity: Entity[_])
  extends RuntimeException(s"$entity is not persisted and therefore can't be updated")

abstract class GenericDao[E <: Entity[E] : ClassTag] { this: SlickDaoProfile =>

  import profile.simple._

  val table : BaseTable[E]

  implicit def toActiveRecord(entity: E) = new {
    def save(): E = {
      entity.id match {
        case None => add(entity)
        case _ => update(entity); entity
      }
    }
    def remove() : Boolean = delete(entity)
  }

  private def addWithAutoInc(entity: E): Long =
    DB.withTransaction {
      implicit sess: Session =>
        table.*.returning(table.id).insert(entity)
    }

  def add(entity: E): E = {
    val id: Long = addWithAutoInc(entity)
    entity.withId(id)
  }

  def update(entity: E) {
    DB.withTransaction {
      implicit sess: Session =>
        entity.id match {
          case Some(id) => filterQuery(id).update(entity)
          case None => throw new CannotUpdateNonPersistedEntityException(entity)
        }
    }
  }

  private def filterQuery(id: Long) = table.filter(_.id === id)

  def delete(entity: E): Boolean = {
    entity.id match {
      case Some(id) => deleteById(id)
      case None => false
    }
  }

  def deleteById(id: Long): Boolean =
    DB.withTransaction {
      implicit sess: Session =>
        filterQuery(id).delete > 0
    }

  def findOptionById(id: Long): Option[E] =
    DB.withTransaction {
      implicit sess: Session =>
        table.createFinderBy(_.id).firstOption(id)
    }

  def findById(id: Long): E =
    findOptionById(id).get


  def pagesList(pageIndex: Int, limit: Int): List[E] =
    DB.withTransaction {
      implicit sess: Session =>
        val query = for {entity <- table} yield entity
        query.list.drop(pageIndex).take(limit)
    }

  def list(): List[E] =
    DB.withTransaction {
      implicit sess: Session =>
        val query = for {entity <- table} yield entity
        query.list
    }
}