package controllers

import play.autosource.slick.{Mapper, SlickAutoSourceController}
import models.Person
import play.api.libs.json.Json

object Application extends SlickAutoSourceController[Person] {

  val dao = Person
  val mapper = PersonMapper

}

object PersonMapper extends Mapper[Person] {
  val format = Json.format[Person]
}
