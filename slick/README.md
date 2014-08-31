# Autosource support for Slick

## Usage

Based on the cake pattern. First, create your model and the corresponding table. It is important that the object extending `BaseTableQuery` is marked as implicit.

~~~ scala
package models

import slick.dao.{SlickDaoProfile, Entity}
import play.api.libs.json.Json

case class Person(
  firstName: String,
  lastName: String,
  id: Option[Long] = None
) extends Entity[Person] {
  def withId(id: Long): Person = copy(id = Some(id))
}

object Person  {
  implicit val formatPerson = Json.format[Person]
}

trait PersonComponent  { this: SlickDaoProfile =>
  import profile.simple._

  class PersonsTable(tag: Tag) extends BaseTable[Person](tag, "persons") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def firstName = column[String]("first_name")
    def lastName = column[String]("last_name")

    def * = (firstName, lastName, id.?) <> ((Person.apply _).tupled, Person.unapply _)
  }

  implicit object Persons extends BaseTableQuery[Person, PersonsTable](new PersonsTable(_)) {}
}
~~~

Next, create the Components and group in there all your models. There is only one here, but you need to add all of yours.

~~~ scala
package models

import slick.dao.SlickDaoProfile
import scala.slick.driver.JdbcProfile
import play.api.db.slick.DB

class Components(override val profile: JdbcProfile)
  extends PersonComponent  with SlickDaoProfile

object Components {
  val instance = new Components(DB(play.api.Play.current).driver)
}
~~~

We can then create the Play controller which will handle the CRUD.

~~~ scala
package controllers

import play.autosource.slick.SlickAutoSourceController
import models.Person
import play.api.libs.json.Json
import models.Components.instance.Persons

object Application extends SlickAutoSourceController[Person] {}
~~~

And finally, we just need to delegate all the routing to that controller inside the `conf/routes` file.

~~~
->      /persons                    controllers.Application
~~~

## TODO

- Slick DAO should become a separated project.
- Should be possible to have different ID types. For the moment only Long is supported.
