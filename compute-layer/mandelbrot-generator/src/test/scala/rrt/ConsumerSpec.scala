package rrt

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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
}
