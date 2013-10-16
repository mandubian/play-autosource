package play.autosource
package core

import play.api.mvc._
import play.api.libs.json._
import scala.concurrent._


/**
  * A hook to execute something before the action
  */
case class BeforeAction[A, T](action: Action[A])(f: A => Future[Unit])(implicit ctx: ExecutionContext) extends Action[A] {

  def apply(request: Request[A]): Future[SimpleResult] = {
    for {
    	_ <- f(request.body)
    	res <- action(request)
    } yield res
  }

  lazy val parser = action.parser
}


/**
  * A hook to execute something after the action
  */
case class AfterAction[A, T](action: Action[A])(f: A => Future[Unit])(implicit ctx: ExecutionContext) extends Action[A] {

  def apply(request: Request[A]): Future[SimpleResult] = {
    for {
    	res <- action(request)
    	_ <- f(request.body)
    } yield res
  }

  lazy val parser = action.parser
}
