package play.autosource.datomisca

import clojure.{lang => clj}

import datomisca.gen.TypedQuery0

object DatomiscaQueryParser {

  private def readEDN(edn: String): Either[String, AnyRef] =
    try {
      Right(datomic.Util.read(edn))
    } catch {
      case ex: RuntimeException =>
        Left(s"The supplied query string does not parse as valid EDN: failure(${ex.getMessage}).")
    }

  private val findKW  = clj.Keyword.intern(null, "find")
  private val inKW    = clj.Keyword.intern(null, "in")
  private val whereKW = clj.Keyword.intern(null, "where")

  private def validateDatalog(edn: AnyRef): Either[String, clj.IPersistentMap] =
    edn match {
      case coll: clj.IPersistentMap =>
        val findClause  = coll.valAt(findKW)
        val inClause    = coll.valAt(inKW)
        val whereClause = coll.valAt(whereKW)
        if ((findClause ne null) &&
            (inClause eq null) &&
            (whereClause ne null) &&
            findClause.isInstanceOf[clj.IPersistentVector] &&
            findClause.asInstanceOf[clj.IPersistentVector].count == 1 &&
            whereClause.isInstanceOf[clj.IPersistentVector]) {
          Right(coll)
        } else {
          Left("The supplied query string was a map that did not match the pattern {:find [?e] :where [...]} .")
        }
      case coll: clj.PersistentVector =>
        if (coll.count > 3) {
          (coll.nth(0), coll.nth(1), coll.nth(2)) match {
            case (`findKW`, sym: clj.Symbol, `whereKW`) =>
              val map = new clj.PersistentArrayMap(Array.empty).asTransient()
              map.assoc(findKW, clj.PersistentVector.create(sym))
              map.assoc(whereKW, coll.subList(3, coll.count))
              Right(map.persistent())
            case _ =>
              Left("The supplied query string was a vector that did not match the pattern [:fine ?e :where ...] .")
          }
        } else {
          Left("The supplied query string was a vector that did not have enough elements.")
        }
      case _ =>
        Left("The supplied query string does not parse as a map or a vector.")
    }

  def parseTypedQuery0(text: String): Either[String, TypedQuery0[Any]] =
    readEDN(text).right.flatMap { edn =>
      validateDatalog(edn).right.map { query =>
        new TypedQuery0[Any](query)
      }
    }

}
