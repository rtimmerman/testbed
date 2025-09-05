package rrt.policy

import rrt.ProducerParamsV2

case class DispatchResult[R](message: String, juliaDimensionResult: R)
case class JuliaDimensionResult(dim: Double, centre: rrt.Complex)

trait Adjustable[R]:
  def afterDispatch(msg: String, fn: () => R): Unit
  def onPerfEvaluation(): Unit = {}
  def onPostDispatchEvaluation(): ProducerParamsV2

