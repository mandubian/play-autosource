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
import scala.slick.driver.ExtendedProfile
import play.api.db.slick.DB
import play.api.Play.current
import scala.slick.session.Session

trait SlickDao[E <: Entity[E]] {

  def count(implicit session: Session): Int

  def add(entity: E)(implicit session: Session): E

  def update(entity: E)(implicit session: Session): Unit

  def delete(entity: E)(implicit session: Session): Boolean

  def deleteById(id: Long)(implicit session: Session): Boolean

  def findOptionById(id: Long)(implicit session: Session): Option[E]

  def findById(id: Long)(implicit session: Session): E

  def pagesList(pageIndex: Int, limit: Int)(implicit session: Session): List[E]

  def list(implicit session: Session): List[E]
}


trait SlickDaoProfile {
  val profile: ExtendedProfile

  import profile.simple._

  abstract class BaseTable[E <: Entity[E]](tableName: String)
    extends Table[E](tableName) with SlickDao[E] {

    def id: Column[Long]

    def count(implicit session: Session): Int =
      Query(this.length).first()


    private def addWithAutoInc(entity: E)(implicit session: Session): Long =
      this.*.returning(this.id).insert(entity)

    def add(entity: E)(implicit session: Session): E = {
      val id: Long = addWithAutoInc(entity)
      entity.withId(id)
    }

    def update(entity: E)(implicit session: Session): Unit =
      entity.id match {
        case Some(id) => filterQuery(id).update(entity)
        case None => throw new CannotUpdateNonPersistedEntityException(entity)
      }


    private def filterQuery(id: Long) = this.filter(_.id === id)

    def delete(entity: E)(implicit session: Session): Boolean =
      entity.id match {
        case Some(id) => deleteById(id)
        case None => false
      }


    def deleteById(id: Long)(implicit session: Session): Boolean = filterQuery(id).delete > 0


    def findOptionById(id: Long)(implicit session: Session): Option[E] = this.createFinderBy(_.id).firstOption(id)

    def findById(id: Long)(implicit session: Session): E = findOptionById(id).get


    def pagesList(pageIndex: Int, limit: Int)(implicit session: Session): List[E] = {
      val query = for {entity <- this} yield entity
      query.list.drop(pageIndex).take(limit)
    }

    def list(implicit session: Session): List[E] = {
      val query = for {entity <- this} yield entity
      query.list
    }

  }

}


class CannotUpdateNonPersistedEntityException(entity: Entity[_])
  extends RuntimeException(s"$entity is not persisted and therefore can't be updated")

