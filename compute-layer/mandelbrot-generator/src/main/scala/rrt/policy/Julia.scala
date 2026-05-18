package rrt.policy

import rrt.policy.Adjustable
import rrt.linalg.Complex
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
        logger.info(s"Dispatch complete, waiting ${waitTime} microseconds for work to complete")
        Thread.sleep(waitTime)

        //
    
        logger.info("Finding result nearest to stable region...")

        val nodeCenterDimensions: List[JuliaDimensionResult] = dispatchResults.map(_.juliaDimensionResult)
        val closestCenter: JuliaDimensionResult = nodeCenterDimensions.maxBy(_.dim)

        // determine zoom-out factor
        // if the all of the dimensions in the set are less than 1 then zoom out of the set
        val zoom: Int = if (closestCenter.dim < 1) {
            val zoom = params.zoomPc / 1e3
            logger.info(s"The closest dimension is less than 1 (${closestCenter.dim}) indicating dusting or tending to infinity, zooming out to find stability ")
            logger.info(s"Previous zoom: ${params.zoomPc}, new zoom: ${zoom}")
            zoom.toInt
        } else { // zoom out by factor of 1000
            // determine zoom-in factor
            // if the S.D. is high then zoom in (try by a factor of 1000... this may have to lowered if precision limits)
            val dimensionMean = nodeCenterDimensions.map(_.dim).sum() / nodeCenterDimensions.length
            val dimensionVariance = nodeCenterDimensions
                .map({_.dim - dimensionMean})
                .map(Math.pow(_, 2))
                .sum()
                / nodeCenterDimensions.length
            val dimensionSD = Math.sqrt(dimensionVariance)
            logger.info(s"Dimension standard deviation: $dimensionSD")

            val zoom = params.zoomPc * 1e3
            logger.info(s"Zooming in: from ${params.zoomPc} to new zoom level: ${zoom}")
            // if the s.d is over 50% then variance is considerable so zoom-in
            if (dimensionSD >= 0.01) zoom.toInt else params.zoomPc
        }

        logger.info(s"Closest center given is ${closestCenter.center.toString}")
        // update the parameters to have one less try, rebalancing can change the dimTendency, but for now
        // change the kubernetes capacity instead.
        // move the centre to the one closest to stability
        val updatedParams = params.copy(
            coordinate = closestCenter.center.toString,
            zoomPc = zoom,
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


