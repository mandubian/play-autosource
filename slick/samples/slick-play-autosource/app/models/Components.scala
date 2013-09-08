package models

import slick.dao.SlickDaoProfile
import scala.slick.driver.ExtendedProfile
import play.api.db.slick.DB

class Components(override val profile: ExtendedProfile)
  extends PersonComponent  with SlickDaoProfile

object Components {
  val instance = new Components(DB(play.api.Play.current).driver)
}
