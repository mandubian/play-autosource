package controllers

import play.autosource.slick.SlickAutoSourceController
import models.Person
import play.api.libs.json.Json

object Application extends SlickAutoSourceController[Person] {

  val dao = Person
  val format = Json.format[Person]

}

