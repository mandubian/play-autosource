package controllers

import play.autosource.slick.SlickAutoSourceController
import models.Person
import play.api.libs.json.Json
import models.Components.instance.Persons

object Application extends SlickAutoSourceController[Person] {

  val dao = Persons
  
}

