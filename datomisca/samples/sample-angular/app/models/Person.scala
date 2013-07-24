package models

import play.api.libs.json._
import play.api.libs.functional.syntax._

import datomisca._
import Datomic._
import DatomicMapping._
import play.modules.datomisca.Implicits._

/** PERSON */

// Ref is a pure technical class used to indicate that it references another entity (also contains the DId temporary or final)
// DRef is a direct reference to a pure non-typed Ident (an enumerator in datomic)
case class Person(name: String, age: Long, characters: Set[DRef])

object Person {
  // Namespaces
  val person = new Namespace("person") {
    val characters = Namespace("person.characters")
  }

  // Attributes
  val name       = Attribute(person / "name",       SchemaType.string, Cardinality.one) .withDoc("Person's name")
  val age        = Attribute(person / "age",        SchemaType.long,   Cardinality.one) .withDoc("Person's age")
  val characters = Attribute(person / "characters", SchemaType.ref,    Cardinality.many).withDoc("Person's characterS")

  // Characters
  val violent = AddIdent(person.characters / "violent")
  val weak    = AddIdent(person.characters / "weak")
  val clever  = AddIdent(person.characters / "clever")
  val dumb    = AddIdent(person.characters / "dumb")
  val stupid  = AddIdent(person.characters / "stupid")

  // Schema
  val schema = Seq(
    name, age, characters,
    violent, weak, clever, dumb, stupid
  )

  // Json Reads/Writes

  implicit val personFormat = Json.format[Person]

  // queryReader
  implicit val partialUpdate: Reads[PartialAddEntity] = (
    ((__ \ 'name).read(readAttr[String](Person.name)) orElse Reads.pure(PartialAddEntity(Map.empty))) and
    ((__ \ 'age) .read(readAttr[Long](Person.age)) orElse Reads.pure(PartialAddEntity(Map.empty)))  and
    // need to specify type because a ref/many can be a list of dref or entities so need to tell it explicitly
    (__ \ 'characters).read( readAttr[Set[DRef]](Person.characters) )
    reduce
  )

  // Entity Reads/Writes
  implicit val entity2Person: EntityReader[Person] = (
    name      .read[String]   and
    age       .read[Long]     and
    characters.read[Set[DRef]]
  )(Person.apply _)

  implicit val person2Entity: PartialAddEntityWriter[Person] = (
    name      .write[String]   and
    age       .write[Long]     and
    characters.write[Set[DRef]]
  )(DatomicMapping.unlift(Person.unapply))
}

