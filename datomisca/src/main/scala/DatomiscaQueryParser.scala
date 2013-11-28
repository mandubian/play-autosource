package play.autosource.datomisca

import clojure.{lang => clj}
import scala.util.{Try, Success, Failure}
import scala.collection.JavaConverters._
import scala.collection._

import datomisca._
import datomisca.gen.TypedQuery0

object DatomiscaQueryParser {
  private def withClojure[T](block: => T): T = {
    val t = Thread.currentThread()
    val cl = t.getContextClassLoader
    t.setContextClassLoader(this.getClass.getClassLoader)
    try block finally t.setContextClassLoader(cl)
  }

  private def readEDN(edn: String): Try[AnyRef] =
    Try {
      withClojure { datomic.Util.read(edn) }
    }

  private def validateDatalog(edn: AnyRef): Either[String, (clj.IPersistentMap, Int, Int)] = {
    val query = edn match {
      case coll: clj.IPersistentMap =>
        Right(coll)
      case coll: clj.PersistentVector =>
        val iter = coll.iterator.asScala.asInstanceOf[Iterator[AnyRef]]
        transformQuery(iter)
      case _ =>
        Left("Expected a datalog query represented as either a map or a vector")
    }

    query.right.flatMap{ query =>
      val outputSize = Option {
          query.valAt(clj.Keyword.intern(null, "find"))
        } map { findClause =>
          Right(findClause.asInstanceOf[clj.IPersistentVector].length)
        } getOrElse { Left("The :find clause is empty") }

      val inputSize = Option {
          query.valAt(clj.Keyword.intern(null, "in"))
        } map { inClause =>
          Right(inClause.asInstanceOf[clj.IPersistentVector].length)
        } getOrElse Right(0)

      for {
        os <- outputSize.right
        is <- inputSize.right
      } yield (query, is, os)
    }

  }

  private def transformQuery(iter: Iterator[AnyRef]): Either[String, clj.IPersistentMap] = {
    def isQueryKeyword(kw: clj.Keyword): Boolean = {
      val name = kw.getName
      (name == "find") || (name == "with") || (name == "in") || (name == "where")
    }
    var currKW: Either[String, clj.Keyword] =
      if (iter.hasNext)
        iter.next() match {
          case kw: clj.Keyword if isQueryKeyword(kw) =>
            Right(kw)
          case x =>
            Left(s"Expected a query clause, found value $x with ${x.getClass}")
        }
      else
        Left("Expected a non-empty vector")

    val map = new clj.PersistentArrayMap(Array.empty).asTransient()
    while (iter.hasNext && currKW.isRight) {
      val clauseKW = currKW
      val buf = mutable.Buffer.empty[AnyRef]
      var shouldContinue = true

      while (shouldContinue && iter.hasNext && currKW.isRight) {
        iter.next() match {
          case kw: clj.Keyword =>
            if (isQueryKeyword(kw)) {
              currKW = Right(kw)
              shouldContinue = false
            } else
              currKW = Left(s"Unexpected keyword $kw in datalog query")

          case o =>
            buf += o
        }
      }

      if (buf.isEmpty)
        currKW = Left(s"The $clauseKW clause is empty")

      if(currKW.isRight) { clauseKW.right.map{ c => map.assoc(c, clj.PersistentVector.create(buf.asJava)) } }
    }

    currKW.right.map{ _ => map.persistent() }
  }

  def parseTypedQuery0(text: String): Either[String, TypedQuery0[DatomicData]] = {
    readEDN(text).map { edn =>
      validateDatalog(edn) match {
        case Left(failure) => Left(s"Request body is not a valid TypedQuery0: failure($failure).")
        case Right((query, inputSize, outputSize)) =>
          if (inputSize > 0)
            Left("Request body is not a valid TypedQuery0: can't accept input params.")
          else if (outputSize > 1)
            Left("Request body is not a valid TypedQuery0: can't accept more than 1 output param.")
          else
            Right(new TypedQuery0[DatomicData](query))
      }
    } match {
      case Success(r) => r
      case Failure(e) => Left(s"Request body is not a valid EDN Query: failure(${e.getMessage}).")
    }
  }
}