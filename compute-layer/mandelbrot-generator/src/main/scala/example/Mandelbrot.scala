package example
import java.util.Properties

//object Mandelbrot extends App with Greeting {
@main def mandelbrot(role: String, topic: String, iterations: Int): Unit =
  println(f"Starting as $role, topic: $topic, iterations = $iterations")
  // println(args)
  // val topic = args(0) match {
  //   case "consumer"             => args(1)
  //   case "data-writer-consumer" => args(1)
  //   case _                      => "test"
  // }

  // val topicPrefix =
  //   if (role.equals("producer") && args.length > 1) topic else "test"
  // val iterations =
  //   if (args.length > 2) args(2).toInt else 10

  if (role.equals("producer"))
    Producer.produceGridPoints(topic, iterations)
  else if (role.equals("consumer"))
    Consumer.consume(topic)
  else if (role.equals("data-writer-consumer"))
    DataWriter.consume(topic)
