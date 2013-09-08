package slick.dao


import scala.language.implicitConversions


trait ActiveRecord[E <: Entity[E]] {

  val slickDao : SlickDao[E]

  implicit def toActiveRecord(entity: E) = new {
    def save(): E = {
      entity.id match {
        case None => slickDao.add(entity)
        case _ => slickDao.update(entity); entity
      }
    }
    def remove() : Boolean = slickDao.delete(entity)
  }

}

