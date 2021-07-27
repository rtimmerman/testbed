package example
import java.util.Properties

object Mandelbrot extends App with Greeting {
  println(args)
  if (args(0).equals("producer"))
    Producer.produceGridPoints()
  else
    Consumer.consume()

  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "hello"
}

