package rrt.policy

import org.scalatest.flatspec.AnyFlatSpec
import rrt.LoggingTrait
import org.scalatest.matchers.should.Matchers
import rrt.Producer
import rrt.ProducerParamsV2
import rrt.linalg.Complex
import rrt.linalg.StringExtension.asComplex

object JuliaSpecHelper:
    def getParams(): ProducerParamsV2 =
        val configFile = System.getProperty("user.dir") + "/src/test/resources/test-work.yml"
        val producerProps = new java.util.Properties()
        Producer.getParams(configFile)

class JuliaSpec extends AnyFlatSpec 
    with Matchers
    with LoggingTrait:
  
  "Stable affine Julia Policy" should "center offset coordinates to stablity - single result works" in {
    val origParams = JuliaSpecHelper.getParams()
    val dispatchResult =DispatchResult("", JuliaDimensionResult(0, "-2-2i".asComplex ))
    val stable = Julia(origParams, List(dispatchResult))

    // stable.afterDispatch()
    val newParams = stable.onPostDispatchEvaluation()
    assert(newParams.isInstanceOf[ProducerParamsV2])
    assert(newParams.coordinate.equals("-2.0-2.0i"))
  }

  "Stable affine Julia Policy" should "center offset coordinates to stability - multiple point, closest center wins" in {
    val origParams = JuliaSpecHelper.getParams()
    val dispatchResults = List(
      DispatchResult("", JuliaDimensionResult(0, "-2-2i".asComplex )),
      DispatchResult("", JuliaDimensionResult(1, "0".asComplex )),
      DispatchResult("", JuliaDimensionResult(0, "-2-2i".asComplex )),
    )
    val stable = Julia(origParams, dispatchResults)

    // stable.afterDispatch()
    val newParams = stable.onPostDispatchEvaluation()
    assert(newParams.isInstanceOf[ProducerParamsV2])
    assert(newParams.coordinate.equals("0".asComplex.toString()))
    assert(newParams.zoomPc != origParams.zoomPc)
    assert(newParams.zoomPc == origParams.zoomPc * 1e3.toInt)
  }

  "Stable affine Julia Policy" should "center on largest dimension and not zoom if the standard deviation is low" in {
    val origParams = JuliaSpecHelper.getParams()
    val dispatchResults = List(
      DispatchResult("", JuliaDimensionResult(0.99, "-2-2i".asComplex )),
      DispatchResult("", JuliaDimensionResult(1, "0".asComplex )),
      DispatchResult("", JuliaDimensionResult(0.99, "-2-2i".asComplex )),
    )
    val stable = Julia(origParams, dispatchResults)

    // stable.afterDispatch()
    val newParams = stable.onPostDispatchEvaluation()
    assert(newParams.isInstanceOf[ProducerParamsV2])
    assert(newParams.coordinate.equals("0".asComplex.toString()))
    assert(newParams.zoomPc == origParams.zoomPc)
  }

  "Stable affine Julia Policy" should "zoom if 4/16 nodes are close to stability" in {
    val origParams = JuliaSpecHelper.getParams()
    // find the dimensions w/grep: cat producer-shell.log  | grep "Dimension: " | less
    /* 2026-05-12 10:49:12.571 [scala-execution-context-global-122] INFO  rrt.Producer$ - Dimension: 1.2132178380915544   // 1
2026-05-12 10:49:12.937 [scala-execution-context-global-124] INFO  rrt.Producer$ - Dimension: 1.249585502688717           // 2
2026-05-12 10:49:13.411 [scala-execution-context-global-121] INFO  rrt.Producer$ - Dimension: 1.2339850002884625          // 3
2026-05-12 10:49:15.058 [scala-execution-context-global-123] INFO  rrt.Producer$ - Dimension: 1.2258566033889935          // 4
2026-05-12 10:50:59.856 [scala-execution-context-global-122] INFO  rrt.Producer$ - Dimension: 1.2533573081389804          // 5
2026-05-12 10:51:00.283 [scala-execution-context-global-124] INFO  rrt.Producer$ - Dimension: 1.2457637380991762          // 6
2026-05-12 10:51:00.536 [scala-execution-context-global-123] INFO  rrt.Producer$ - Dimension: 1.2339850002884625          // 7
2026-05-12 10:51:03.112 [scala-execution-context-global-121] INFO  rrt.Producer$ - Dimension: 1.2457637380991762          // 8
2026-05-12 10:53:15.867 [scala-execution-context-global-123] INFO  rrt.Producer$ - Dimension: 1.2533573081389804          // 9
2026-05-12 10:53:20.261 [scala-execution-context-global-124] INFO  rrt.Producer$ - Dimension: 1.249585502688717           // 10
2026-05-12 10:53:21.582 [scala-execution-context-global-121] INFO  rrt.Producer$ - Dimension: 1.2418906731257902          // 11
2026-05-12 10:53:22.733 [scala-execution-context-global-122] INFO  rrt.Producer$ - Dimension: 1.2570804437724497          // 12
2026-05-12 10:55:48.709 [scala-execution-context-global-123] INFO  rrt.Producer$ - Dimension: 1.2570804437724497          // 13
2026-05-12 10:55:59.001 [scala-execution-context-global-122] INFO  rrt.Producer$ - Dimension: 1.2379649117760034          // 14
2026-05-12 10:55:59.264 [scala-execution-context-global-124] INFO  rrt.Producer$ - Dimension: 1.2457637380991762          // 15
2026-05-12 10:56:00.597 [scala-execution-context-global-121] INFO  rrt.Producer$ - Dimension: 1.2533573081389804          // 16 */

    val dispatchResults = List(
      DispatchResult("", JuliaDimensionResult(1.2132178380915544, "0".asComplex)),   // 1
      DispatchResult("", JuliaDimensionResult(1.249585502688717, "1".asComplex)),              // 2
      DispatchResult("", JuliaDimensionResult(1.2339850002884625, "2".asComplex)),             // 3
      DispatchResult("", JuliaDimensionResult(1.2258566033889935, "3".asComplex)),             // 4
      DispatchResult("", JuliaDimensionResult(1.2533573081389804, "4".asComplex)),             // 5
      DispatchResult("", JuliaDimensionResult(1.2457637380991762, "5".asComplex)),             // 6
      DispatchResult("", JuliaDimensionResult(1.2339850002884625, "6".asComplex)),              // 7
      DispatchResult("", JuliaDimensionResult(1.2457637380991762, "7".asComplex)),             // 8
      DispatchResult("", JuliaDimensionResult(1.2533573081389804, "8".asComplex)),             // 9
      DispatchResult("", JuliaDimensionResult(1.2418906731257902, "9".asComplex)),             // 10
      DispatchResult("", JuliaDimensionResult(1.2457637380991762, "10".asComplex)),             // 11
      DispatchResult("", JuliaDimensionResult(1.2570804437724497, "11".asComplex)),             // 12
      DispatchResult("", JuliaDimensionResult(1.2570804437724497, "12".asComplex)),             // 13
      DispatchResult("", JuliaDimensionResult(1.2379649117760034, "13".asComplex)),             // 14
      DispatchResult("", JuliaDimensionResult(1.2457637380991762, "14".asComplex)),             // 15
      DispatchResult("", JuliaDimensionResult(1.2533573081389804, "15".asComplex)),             // 16
    )


    val stable = Julia(origParams, dispatchResults)

    // stable.afterDispatch()
    val newParams = stable.onPostDispatchEvaluation()
    assert(newParams.isInstanceOf[ProducerParamsV2])

    val expectedNewCandidateCoordinates = dispatchResults.filter(_.juliaDimensionResult.dim == dispatchResults.map(_.juliaDimensionResult.dim).max)
    val std = 
    logger.info(s"Number of candiate newCoordinates = ${expectedNewCandidateCoordinates.length}")
    expectedNewCandidateCoordinates.foreach(cc => logger.info(s"Candidate Coordinate: ${cc.juliaDimensionResult.center}"))
    logger.info(s"Number of candiate newCoordinates = ${expectedNewCandidateCoordinates.length}")

    assert(expectedNewCandidateCoordinates.map(_.juliaDimensionResult.center.toString()).contains(newParams.coordinate))
    assert(newParams.zoomPc == origParams.zoomPc * 1e3.toInt)
  }
