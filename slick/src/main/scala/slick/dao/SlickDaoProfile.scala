/**
 * Copyright 2013 Renato Guerra Cavalcanti (@renatocaval)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package slick.dao

import scala.languageFeature.implicitConversions
import scala.slick.driver.JdbcProfile
import scala.slick.lifted.Tag
import play.api.db.slick.DB
import play.api.Play.current
import play.api.db.slick.Session

trait SlickAutoSource[E <: Entity[E]] {

  def count(implicit session: Session): Int

  def insert(entity: E)(implicit session: Session): E

  def get(id: Long)(implicit session: Session): Option[E]

  def delete(id: Long)(implicit session: Session): Boolean

  def delete(entity: E)(implicit session: Session): Boolean

  def update(entity: E)(implicit session: Session): Unit

  def find(limit: Int = 0, skip: Int = 0)(implicit session: Session): Seq[E]
}


trait SlickDaoProfile {
  val profile: JdbcProfile

  import profile.simple._

  abstract class BaseTable[E <: Entity[E]](tag: Tag, tableName: String)
    extends Table[E](tag, tableName) {

    def id: Column[Long]
  }

  abstract class BaseTableQuery[E <: Entity[E], T <: BaseTable[E]](cons: Tag ⇒ T)
    extends TableQuery[T](cons)
    with SlickAutoSource[E] {

    private def filterQuery(id: Long) = this.filter(_.id === id)

    def count(implicit session: Session): Int =
      this.length.run

    def insert(entity: E)(implicit session: Session): E = {
      val id: Long = (this returning this.map(_.id)) += entity
      entity.withId(id)
    }

    def get(id: Long)(implicit session: Session): Option[E] = filterQuery(id).firstOption

    def delete(id: Long)(implicit session: Session): Boolean = filterQuery(id).delete > 0

    def delete(entity: E)(implicit session: Session): Boolean =
      entity.id match {
        case Some(id) => delete(id)
        case None => false
      }

    def update(entity: E)(implicit session: Session): Unit =
      entity.id match {
        case Some(id) => filterQuery(id).update(entity)
        case None => throw new CannotUpdateNonPersistedEntityException(entity)
      }

    def find(limit: Int = 0, skip: Int = 0)(implicit session: Session): Seq[E] =
      this.drop(skip).take(limit).list

  }
}

class CannotUpdateNonPersistedEntityException(entity: Entity[_])
  extends RuntimeException(s"$entity is not persisted and therefore can't be updated")
