package rrt.policy

import rrt.ProducerParamsV2
import rrt.linalg.Complex

case class DispatchResult[R](message: String, juliaDimensionResult: R)
case class JuliaDimensionResult(dim: Double, center: Complex)

trait Adjustable[R]:
  def afterDispatch(msg: String, fn: () => R): Unit
  def onPerfEvaluation(): Unit = {}
  def onPostDispatchEvaluation(): ProducerParamsV2

