package example
import java.util.Properties

object Mandelbrot extends App with Greeting {

  val topic =
    if (args(0).equals("consumer") && args.length > 1) args(1) else "test"
  val topicPrefix =
    if (args(0).equals("producer") && args.length > 1) args(1) else "test"
  val iterations =
    if (args.length > 2) args(2).toInt else 10

  if (args(0).equals("producer"))
    Producer.produceGridPoints(topicPrefix, iterations)
  else if (args(0).equals("consumer"))
    Consumer.consume(topic)

  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "hello"
}
