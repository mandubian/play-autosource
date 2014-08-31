package slick.dao


import scala.language.implicitConversions
import play.api.db.slick.Session


trait ActiveRecord[E <: Entity[E]] {

  val slickDao : SlickDao[E]

  implicit def toActiveRecord(entity: E) = new {
    def save(implicit session: Session): E = {
      entity.id match {
        case None => slickDao.add(entity)
        case _ => slickDao.update(entity); entity
      }
    }
    def remove(implicit session: Session) : Boolean = slickDao.delete(entity)
  }

}
