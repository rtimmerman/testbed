package rrt.policy

import rrt.policy.Adjustable
import rrt.Complex
import rrt.external.PerformanceEvaluator
import rrt.ProducerParamsV2
import rrt.ProducerParamsV2Extension._
import rrt.ProducerWorkPolicy
import rrt.JuliaPolicy

enum JuliaStrategy:
    case STABILITY, INSTABILITY

class Julia[R](val params: ProducerParamsV2, var dispatchResults: List[DispatchResult[R]] = List()) extends Adjustable[R]:
    
    override def afterDispatch(msg: String, fn: () => R): Unit = 
        dispatchResults = dispatchResults ::: List(DispatchResult(msg, fn()))

    /**
     * After all dispatches have occurred, after a period a time (presumably after the entire
     * set has beeen processed), let's move the julia center and re-run the simulation
     * the outcome should a strategy for faster period scaling given a similarly sized
     * data set.
     */
    def onPostDispatchEvaluation(): ProducerParamsV2 =
        Thread.sleep(params.policy.juliaPolicy.tryIntervalSec * 1000)
    
        val closestCentre: JuliaDimensionResult = dispatchResults match
            case r: List[DispatchResult[JuliaDimensionResult]] => r.map {r => r.juliaDimensionResult}.reduce { (a, b) => if a.dim > b.dim then a else b }
        
        closestCentre match
            case cc: JuliaDimensionResult =>
                params.copy(
                    coordinate = cc.centre.toString,
                    policy = ProducerWorkPolicy(
                        juliaPolicy = JuliaPolicy(
                            maxTries = params.policy.juliaPolicy.maxTries - 1,
                            maxEvalUnits = params.policy.juliaPolicy.maxEvalUnits,
                            tryIntervalSec = params.policy.juliaPolicy.tryIntervalSec,
                            dimTendency = params.policy.juliaPolicy.dimTendency
                        ),
                            performancePolicy=null,
                        nonePolicy = null
                    )
                )
            case _ => params


