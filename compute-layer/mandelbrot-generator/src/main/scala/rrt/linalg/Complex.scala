package rrt.linalg

class Complex(var r: BigDecimal, var i: BigDecimal):
  def + (other: Complex): Complex = new Complex(this.r + other.r, this.i + other.i)

  def += (other: Complex): Complex = Complex(this.r + other.r, this.i + other.i)

  def - (other: Complex): Complex = new Complex(this.r - other.r, this.i - other.i)
  

  def * (other: Complex): Complex = new Complex(
        this.r * other.r - (other.i * this.i), 
        this.r * other.i + this.i * other.r
  )

  def pow(n: BigDecimal): Complex = this**(n)
  def **(n: BigDecimal): Complex = 
    var ans: Complex = this
    try
      Range(0, n.toInt - 1).foreach(_ => ans = ans * this)
    catch
      case e: ArithmeticException => 0
    return ans
  
  def **(w: Complex): Complex =
    val mod = Math.hypot(this.r.toDouble, + this.i.toDouble)
    val arg = Math.atan2(this.i.toDouble, this.r.toDouble)

    val expReal = w.r.toDouble * Math.log(mod) - w.i.toDouble * arg
    val expImag = w.r.toDouble * arg + w.i.toDouble * Math.log(mod)
    val mag = Math.exp(expReal)

    Complex(
      mag * Math.cos(expImag),
      mag * Math.sin(expImag)
    )

  override def equals(n: Any): Boolean =
    if (n.isInstanceOf[Complex])
      return n.asInstanceOf[Complex].r == this.r &&  n.asInstanceOf[Complex].i == this.i
    return false


  // def abs(): Double = Math.pow(r.pow(2).toDouble + i.pow(2).toDouble, 0.5)
  def abs(): Double =
    try
      Math.hypot(r.toDouble, i.toDouble)
    catch case e: ArithmeticException => 0

  // def conj(): Complex = new Complex(this.r, -this.i)
  def conj: Complex = Complex(this.r, -this.i)

  override def toString(): String = s"${r.toDouble}${if i >= 0 then "+" else ""}${i.toDouble}i"

object Complex:
  def fromString(str: String): Complex = {
    val complexRegex = "(([-+]?[0-9.]+)i?)".r
    var r: BigDecimal = 0
    var i: BigDecimal = 0
    for (m <- complexRegex.findAllMatchIn(str)) {
      val candidate = m.group(1)
      if (candidate.contains("i"))
        i = BigDecimal(m.group(2))
      else
        r = BigDecimal(candidate)
    }

    return Complex(r, i)
  }

  def fromMap(entry: Map[String, Double]): Complex =
    return Complex(
      BigDecimal(entry.get("r").get),
      BigDecimal(entry.get("i").get)
    )
  
  def min(a: List[Complex]) =
      a.reduce((x, y) => if x.abs() <= y.abs() then x else y)

object MapExtension:
    extension (m: Map[String, Double])
        def asComplex() =
            Complex(m("r"), m("i"))
