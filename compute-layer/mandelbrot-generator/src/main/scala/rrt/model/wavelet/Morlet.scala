package rrt.model.wavelet

import rrt.linalg.Complex

/**
  * Morelet Wavelet transforms (Real)
  */
class Morlet():
    /** provides the mother wavelet from scale -2*pi to 2*pi
     *  @param bins number of bins for the wavelet, default is 100.
     */
    def getMotherWavelet(bins: Integer = 100, param: Double = 5.5): Seq[Complex] =
        val real = BigDecimal(-2*Math.PI) to BigDecimal(2*Math.PI) by BigDecimal((4 * Math.PI) / bins)
        // val real = BigDecimal(-5) to BigDecimal(5) by BigDecimal(5.0 / bins)
        val admCoeff = Math.exp(-1/2 * Math.pow(param, 2))
        val norm = (1 + (Math.exp(param) - 2*(Math.exp(-3/4 * Math.PI * Math.pow(param, 2)))))

        real.map((t) => t.toDouble).map { t => 
          // println(f"$t: ${rrt.Complex(t, 0) * rrt.Complex(Math.PI, 0) ** rrt.Complex(Math.PI / 4, 0)}")
          Complex(norm, 0)
            * Complex(t, 0) * Complex(Math.PI, 0) ** Complex(Math.PI / 4, 0)
            * Complex(Math.exp(-0.5*Math.pow(t, 2)), 0)
            * ((Complex(Math.E, 0) ** (Complex(param, 0) * Complex(t, 0) * Complex(0, 1))) - Complex(admCoeff, 0))
        }

object Morlet:
    def convolve(rawSignal: Array[Double], wavelet: Array[Complex]): Array[Complex] =
      val flippedConj = wavelet.reverse.map(_.conj)
      val result = Array.ofDim[Complex](rawSignal.length)
      val k = flippedConj.length / 2

      val signal = rawSignal.map(Complex(_,0))

      for (i <- flippedConj.indices)
        var acc = Complex(0.0, 0.0)
        for (j <- flippedConj.indices)
          val idx = i - k + j
          if (idx >= 0 && idx < signal.length)
            acc += signal(idx) * flippedConj(j)
        result(i) = acc
      result

