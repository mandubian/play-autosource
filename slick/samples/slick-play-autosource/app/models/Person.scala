package models

import slick.dao.{SlickDaoProfile, Entity}
import play.api.libs.json.Json

case class Person(firstName: String,
                  lastName: String,
                  id: Option[Long] = None) extends Entity[Person] {

  def withId(id: Long): Person = copy(id = Some(id))

}
object Person  {
  implicit val formatPerson = Json.format[Person]
}

trait PersonComponent  { this: SlickDaoProfile =>

  import profile.simple._

  implicit object Persons extends BaseTable[Person]("persons") {

    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

    def firstName = column[String]("first_name")

    def lastName = column[String]("last_name")

    def * = firstName ~ lastName ~ id.? <>(Person.apply _, Person.unapply _)
  }
}


