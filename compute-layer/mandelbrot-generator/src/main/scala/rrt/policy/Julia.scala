package rrt.policy

import rrt.policy.Adjustable
import rrt.Complex
import rrt.external.PerformanceEvaluator
import rrt.ProducerParamsV2
import rrt.ProducerParamsV2Extension._
import rrt.ProducerWorkPolicy
import rrt.JuliaPolicy
import rrt.LoggingTrait

enum JuliaStrategy:
    case STABILITY, INSTABILITY

class Julia(val params: ProducerParamsV2, var dispatchResults: List[DispatchResult[JuliaDimensionResult]] = List()) extends Adjustable[JuliaDimensionResult], LoggingTrait:
    
    override def afterDispatch(msg: String, fn: () => JuliaDimensionResult): Unit = 
        dispatchResults :::= List(DispatchResult(msg, fn()))

    /**
     * After all dispatches have occurred, after a period a time (presumably after the entire
     * set has beeen processed), let's move the julia center and re-run the simulation
     * the outcome should a strategy for faster period scaling given a similarly sized
     * data set.
     */
    def onPostDispatchEvaluation(): ProducerParamsV2 =
        val waitTime = params.policy.juliaPolicy.tryIntervalSec * 1000
        logger.info(s"Dispatch complete, waiting ${waitTime} secs for work to complete")
        Thread.sleep(waitTime)
    
        logger.info("Finding result nearest to stable region...")
        val closestCentre: JuliaDimensionResult = dispatchResults
            .map {r => r.juliaDimensionResult}
            .maxBy {_.dim}

        logger.info(s"Closest centre given is ${closestCentre.centre.toString}")
        // update the parameters to have one less try, rebalancing can change the dimTendency, but for now
        // change the kubernetes capacity instead.
        // move the centre to the one closest to stability
        val updatedParams = params.copy(
            coordinate = closestCentre.centre.toString,
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

        logger.info("sending updated map accorindg to new centre")
        updatedParams


