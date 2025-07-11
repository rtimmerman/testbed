package rrt.model.wavelet
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Tag

object MorletTag extends Tag("rrt.model.wavelet.Morlet")

class MorletSpec extends AnyFlatSpec with Matchers {
    "Morlet model" should "be able to provide the mother wavelet" taggedAs(MorletTag) in  {
        val m = Morlet()
        val shape = m.getMotherWavelet()
        println(shape)
    }
}
