package example
import java.util.Properties

@main def mandelbrot(role: String, args: String*): Unit =
  val topic = role match {
    case "producer"             => args(0)
    case "consumer"             => args(0)
    case "data-writer-consumer" => args(0)
    case _                      => "test"
  }

  val iterations = role match {
    case "producer" => args(1).toInt
    case _ => 0
  }

  if (role.equals("producer"))
    println(f"<< PRODUCER >> submitting to topic: \"$topic\", # iterations = $iterations")
    Producer.produceGridPoints(topic, iterations)
  else if (role.equals("consumer"))
    println(f"<< CONSUMER >> listening to \"$topic\"")
    Consumer.consume(topic)
  else if (role.equals("data-writer-consumer"))
    println(f"<< DATA-WRITER >> listnening to \"$topic\"")
    DataWriter.consume(topic)
