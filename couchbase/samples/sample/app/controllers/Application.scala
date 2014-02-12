package controllers

import play.api.libs.json._
import org.ancelin.play2.couchbase.Couchbase
import play.api.Play.current
import play.autosource.couchbase.CouchbaseAutoSourceController
import play.api.libs.concurrent.Execution.Implicits._
import org.reactivecouchbase.play.PlayCouchbase

case class Person(name: String, surname: String, datatype: String = "person")

object Person {
  implicit val fmt = Json.format[Person]
}

object PersonController extends CouchbaseAutoSourceController[Person] {
  def bucket = PlayCouchbase.bucket("default")
  def defaultViewName = "by_name"
  def defaultDesignDocname = "persons"
}
