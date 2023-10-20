package example

import org.slf4j.LoggerFactory

trait LoggingTrait {
  val logger = LoggerFactory.getLogger(this.getClass().getName())
}
