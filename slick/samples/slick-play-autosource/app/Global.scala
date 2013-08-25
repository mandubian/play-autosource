import models.Person
import play.api.GlobalSettings


import play.api.Application

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Person("John", "Doe").save()
    Person("João", "da Silva").save()
  }
}