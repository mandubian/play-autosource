import models.Person
import play.api.GlobalSettings


import play.api.Application

import models.Components.instance._

object Global extends GlobalSettings {

  override def onStart(app: Application) {
    Persons.add(Person("John", "Doe"))
    Persons.add(Person("João", "da Silva"))
  }
}