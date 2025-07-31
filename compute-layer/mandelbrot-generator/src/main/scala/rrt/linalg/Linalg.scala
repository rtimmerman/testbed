/**
  * # RRT Linalg
  * This package aims to provide rudimentary linear algebra operators alongside those specific to this project
  * Included for example is the Kronecker product, dot product, scalar multiplication and addition
  * 
  * The package specialised on Array[Array[Double]] e.g.
  * [[0, 0],
  * [0, 0]]
  * 
  * By aliasing it as **Matrix2DType** and adding functions as extensions to anything matching that type
  * There is a helper Object called Matrix2D which allows filled matrix generation e.g. $0_{n{\times}m}$ and $1_n{\times}m$
  * 
  * No promises on speed here, no GPU shannigans this time!
  */
package rrt.linalg

type Matrix2DType = Array[Array[Double]]
object ArrayExtension:
    extension (a: Matrix2DType)
        def fill(content: Double) =
            for (row <- a) yield a.map((col) => content)
            
        def entryAt(rowIdx: Int, colIdx: Int) =
            a(rowIdx)(colIdx)

        def add(other: Matrix2DType): Matrix2DType =
            (0 to a.length - 1).map((i) => (0 to a(i).length - 1).map((j) => a(i)(j) + other(i)(j)).toArray).toArray

        def dot(other: Matrix2DType): Matrix2DType =
            val cWidth = other(0).length
            val cHeight = a.length
            (for (j <- 0 to cHeight - 1)
                yield (for (i <- 0 to cWidth - 1)
                    yield (0 to a(0).length - 1).map((k) => a(i)(k) * other(k)(j)).sum).toArray
                .toArray).toArray
        
        def scalarMul(alpha: Double) =
            a.map((row) => 
                    row.map((col) => col * alpha)
                )

        def prettyString: String =
            val content = for (j <- 0 to a.length - 1; i <- 0 to a(0).length - 1)
                yield f"${a.transpose.entryAt(i, j)} " + (if i == a.length - 1 then "\n" else ", ")
            "\n--------\n" 
                + content.reduce(_+_)
                + "\n========\n"
        
        def kronecker(n: Int, m: Int): Matrix2DType =
            val ones = Matrix2D.ones(n, m)
            for {j <- a; _ <- 1 to m}
                yield j.flatMap(e => Array.fill(m)(e))

object Matrix2D:
    def ofShape(n: Int, m: Int, content: Double = 0d): Matrix2DType =
        (for (j <- 0 to m - 1) yield Array.fill(n){content}).toArray
    def ones(n: Int, m: Int): Matrix2DType = ofShape(n, m, 1)
    def zeros(n: Int, m: Int): Matrix2DType = ofShape(n, m, 0)