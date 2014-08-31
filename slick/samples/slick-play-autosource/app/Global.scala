import play.api.GlobalSettings
import play.api.db.slick.{DB, Session}
import models.Person
import play.api.Play.current
import play.api.Application

import models.Components.instance._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    DB.withTransaction { implicit session : Session =>
      Persons.add(Person("John", "Doe"))
      Persons.add(Person("Joao", "da Silva"))
    }
  }
}
