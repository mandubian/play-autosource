package controllers

import play.api._
import play.api.mvc._

import play.api.libs.json._
import play.api.Play.current

import org.ancelin.play2.couchbase.Couchbase
import play.api.Play.current
import play.autosource.couchbase.CouchbaseAutoSourceController
import play.api.libs.concurrent.Execution.Implicits._

case class Person(name: String, surname: String, datatype: String = "person")

object Person {
  implicit val fmt = Json.format[Person]
}

object PersonController extends CouchbaseAutoSourceController[Person] {
  val bucket = Couchbase.bucket("default")
  val defaultViewName = "by_name"
  val defaultDesignDocname = "persons"
}
