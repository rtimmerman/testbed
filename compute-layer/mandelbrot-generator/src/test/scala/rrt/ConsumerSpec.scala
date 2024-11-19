package rrt

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.{Await,Future}
import io.prometheus.client.exporter.HTTPServer
import scala.concurrent.duration.*

class ConsumerSpec extends AnyFlatSpec with Matchers {
  "Complex number" should "be able to generate product" in {
    val a = new Complex(2, 2)
    val b = new Complex(3, 3)

    val prod = a * b
    assert(prod.r == 0)
    assert(prod.i == 12)
  }

  "Complex number" should "be able to calculate sum" in {
    val a = new Complex(2, 2)
    val b = new Complex(4, 5)

    val prod = a + b
    assert(prod.r == 6)
    assert(prod.i == 7)
  }

  "Complex number" should "be able to calculate absolute value" in {
    val a = new Complex(3, 4)
    assert(a.abs() == 5)
  }

  "Consumer" should "be able to emit prometheus counter statistics" in {
    val statsServer = HTTPServer.Builder()
        .withPort(9180)
        //.withDaemonThreads(true)
        .build()
    
    println(s"statsServer running on port ${statsServer.getPort()}")
    println("not blocked!")
    RrtMetrics.appendDwellTime(10)
    //Await.result(statsServer, 30.seconds).stop()
    statsServer.close()
  }
}
