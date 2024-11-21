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

  "Consumer" should "be able to process 0-i" in {
    val r = 0
    val i = -1
    val z = Complex(BigDecimal(1), BigDecimal(0))
    val res = Consumer.process(z, z, 1000)
    assert(res.equals(999))
  }

  "Consumer" should "know how to calculate complex power" in {
    val z = Complex(BigDecimal(2), BigDecimal(0))
    // println(z**2)
    // println(z.pow(2))
    assert((z**2).equals(z.pow(2)))
  }

  "Consumer" should "not break on 0-0i" in {
    val r = 0.00000
    val i = -1.00000
    val z = Complex(BigDecimal(r), BigDecimal(i))
    val res = Consumer.process(z, z, 1000)
    // println(res)
    assert(res.equals(-1))
  }

  // "Consumer" should "know how to handle an exception" in {
  //   try {
  //     val r = 1/0
  //   } catch {
  //     case e: Exception =>
  //       println(e.getClass())
  //       e.getStackTrace().foreach(t => println(s"<ERR> $t ${t.getArg}"))
  //       if (e.getCause() != null)
  //         println(s"<ERR> Caused by: ${e.getCause().getClass()}")
  //   }
  // }
}
