package models

import slick.dao.{SlickDaoProfile, GenericDao, Entity}
import play.api.libs.json.Json

case class Person(firstName: String,
                  lastName: String,
                  id: Option[Long]) extends Entity[Person] {

  def withId(id: Long): Person = copy(id = Some(id))

}


object Person extends GenericDao[Person] with SlickDaoProfile {

  val table = models.Components.instance.Persons

  implicit val personFormat = Json.format[Person]

  trait PersonComponent {

    object Persons extends BaseTable[Person]("persons") {

      def id = column[Long]("id", O.PrimaryKey, O.AutoInc)

      def firstName = column[String]("first_name")

      def lastName = column[String]("last_name")

      def * = firstName ~ lastName ~ id.? <>(Person.apply _, Person.unapply _)
    }
  }
}


