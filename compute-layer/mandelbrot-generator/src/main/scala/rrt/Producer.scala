package rrt
import java.util.{Properties, UUID}
import org.apache.kafka.clients.producer._
// import breeze.math._
// import breeze.linalg._

import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.util.{Success, Failure}
import scala.util.Random

import org.slf4j.{Logger,LoggerFactory}
import java.math.RoundingMode
import rrt.external.PerformanceEvaluator
import scala.collection.mutable.Queue

import rrt.ProducerParamsV2Extension._
import rrt.linalg.ArrayExtension.kronecker

import rrt.policy.Julia as JuliaPolicy
import rrt.policy.JuliaDimensionResult
import rrt.policy.DispatchResult
import rrt.policy.Adjustable

object Producer extends KafkaTrait {
  type Space = Array[Array[Map[String, Double]]];

  def createSpaceFromPoint(c: rrt.Complex, zoomPc: Integer = 100, ball: Integer = 1000, canvasDimensions: Map[String, Int] = Map("minR" -> -2, "maxR" -> 2, "minI" -> -2, "maxI" -> 2)): Space = {
    val scale = 1 / (zoomPc.toDouble / 100)
    val linspace = (x1: BigDecimal, x2: BigDecimal, count: BigDecimal) => (x1 * scale to x2 * scale by (((x1  * scale) - (x2 * scale)).abs / (count)))
    val R = linspace(canvasDimensions("minR"), canvasDimensions("maxR"), BigDecimal(ball))
    val I = linspace(canvasDimensions("minI"), canvasDimensions("maxI"), BigDecimal(ball))
    // the point is c

    // transform the points by adding c and multiplying them by scale across the initial space.
    val tR = R.map(e => (e + c.r))
    val tI = I.map(e => (e + c.i)).reverse

    val b = Array.ofDim[Map[String, Double]](ball + 1, ball + 1)
    for (i <- tI; r <- tR)
      b(tR.indexOf(r))(tI.indexOf(i)) = Map("r" -> r.toDouble, "i" -> i.toDouble)

    return b
  }

  def createSpace(sizeX: Int, sizeY: Int, minR: Double, maxR: Double, minI: Double, maxI: Double): Space = {
    val b = Array.ofDim[Map[String, Double]](sizeX + 1, sizeY + 1)

    val linspace = (x1: BigDecimal, x2: BigDecimal, count: BigDecimal) => (x1 to x2 by ((x1 - x2).abs/count))

    val toCoord = (x1: Double, x2: Double, scale: Double, point: BigDecimal) => linspace(x1, x2, scale).indexOf(point)

    val I = linspace(minI, maxI, sizeY).reverse
    val R = linspace(minR, maxR, sizeX)
    for (i <- I; r <- R)
      val x = R.indexOf(r)
      val y = I.indexOf(i)
      b(x)(y) = Map("r" -> r.toDouble, "i" -> i.toDouble)

    return b
  }

  def partition(space: Space, nPartitions: Int): Seq[Array[Map[String, Double]]] =
    val width, height = (space.length / Math.sqrt(nPartitions)).toInt

    for (x <- 0 to  Math.sqrt(nPartitions).toInt - 1; y <- 0 to Math.sqrt(nPartitions).toInt - 1)
      yield space
        .slice((y * height), (y * height) + height + 1)
        .map(a => a.slice((x * width), (x * width) + width + 1))
        .flatten
  
  def juliaCentre(grp: Array[Map[String, Double]]): Complex =
    val nums = grp.map(rrt.Complex.fromMap)
    val r = nums.map(_.r).min + (nums.map(_.r).min - nums.map(_.r).max).abs / 2
    val i = nums.map(_.i).min + (nums.map(_.i).min - nums.map(_.i).max).abs / 2

    val centre = rrt.Complex(r, i) //<- find the midpoint using averages between real and imaginary parts separately
    logger.atDebug.log(f"Julia center found: $centre")
    centre

  def getJuliaDimension(c: Complex, iterations: Int, nBoxes: Int): Double =
    import math._
    import rrt.linalg._
    import rrt.linalg.ArrayExtension._
    val nBoxesSq = sqrt(nBoxes)

    // now calculate the box count dimension the number of iterations will match the config
    var d = 0
    var blowup = 2.0

    // establish a 1600x1600 julia plot
    val res = BigDecimal(1) / BigDecimal(16) // i.e. 4/1600

    val z_plane = (BigDecimal(-2) until BigDecimal(2) by res)
      .map((i) => (BigDecimal(-2) until BigDecimal(2) by res)
      .map((r) => rrt.Complex(r,i)))

    lazy val m: (rrt.Complex, Int) => Int = (z, n) => {
      val z2 = (z ** 2) + c
      z2.abs() match
        case v if v >= blowup => iterations - n
        case v if n > 0 => 
          m(z2, n - 1)
        case _ => -1
    }

    val boxThreshold = iterations

    // also pay attention to issues around underflow errors here.
    val julia = z_plane.flatten.map {case z =>
      val e = m(z, iterations) 
      e match
        case v if v == -1 || Range(6, 8).contains(v) => v
        case _ => 0
    }

    logger.info(f"max escape iteration = ${julia.max}")

    // -- uncomment to preview julia plot (graphically)
    julia.sliding(64, 64).foreach(row =>
      row.foreach(column => print(column match
        case x if x == -1 => "*"
        case x if x > 0 => List("+","-",".")(x % 3)
        case _ => " "
      ))
      logger.info("")
      )

    val kronLength: Int = (64 / sqrt(nBoxes)).toInt
    val boxlayout = (0 until nBoxes).map(i => i.toDouble).toArray.sliding(nBoxes, nBoxes).toArray.kronecker(kronLength, kronLength)
    val boxAlloc = boxlayout.flatten.map {i => i.toInt}
    assert(boxAlloc.length >= julia.length, f"Julia length: ${julia.length} >= Box Allocations length = ${boxAlloc.length}")
    logger.atInfo.log(f"Box Allocation test: End Box Alloc = ${boxAlloc(julia.length - 1)}, Box alloc length = ${boxAlloc.length}, Julia Length = ${julia.length}, iterations=${iterations}")

    val boxes = Array.ofDim[Int](boxAlloc(boxAlloc.length - 1) + 1)
    for (i <- Range(0, julia.length))
      val boxIndex = boxAlloc(i)
      val valid = (v: Double) => (v != 0)

      if (valid(julia(i).toDouble))
        boxes(boxAlloc(i)) += 1

    val nR = boxes.filter((b) => b > 0).length
    logger.atInfo.log(boxes.toList.toString)
    logger.atInfo.log(f"Number of filled boxes ${nR} out of ${boxes.length}")
    val D = 2 * (log(nR) / log(nBoxes))
    logger.atInfo.log(f"Dimension: $D")
    D

  def getParams(configFilePath: String): ProducerParamsV2 =
    var mapper = new ObjectMapper(new YAMLFactory())
    mapper.readValue(new File(configFilePath), classOf[ProducerParamsV2])

  /**
   * This function is responsible for processing a work request given as a set of parameters.
   * 
   */
  def gridWorkStream(
      producerFactory: (String) => (Producer[String, String]),
      params: ProducerParamsV2
  ): List[rrt.policy.DispatchResult[JuliaDimensionResult]] =
    val runUUID = UUID.randomUUID()

    var b: Array[Array[Map[String,Double]]] = null
    var space = 0

    logger.info(s"Received v2 parameter set")
    logger.info(params.getClass.toString)
    b = createSpaceFromPoint(rrt.Complex.fromString(params.coordinate), params.zoomPc, params.neighbourhoodSize)
    space = params.neighbourhoodSize * params.neighbourhoodSize
    logger.info(s"Run: (Zoom %: ${params.zoomPc} | initial entry: ${rrt.Complex.fromMap(b(0)(0))})")

    val observer: Option[Adjustable[JuliaDimensionResult]] = if (params.usingJuliaPolicy) Some(JuliaPolicy(params)) else None

    val evalUnits = params match
      case p: ProducerParamsV2 if p.usingPerformancePolicy => p.policy.performancePolicy.maxEvalUnits
      case p: ProducerParamsV2 if p.usingNoPolicy => p.policy.nonePolicy.blockSize
      case _ => 160
    
    val workset = b.map(_.sliding(evalUnits, evalUnits).toArray).toArray

    // perf-eval loop

    def workGenerator(workset: Array[Array[Array[Map[String,Double]]]]) =
      for (i <- Range(0, workset(0).toList.length))
        yield workset.map(_.toList(i).toArray).toArray

    def getProgressBar(percent: Double, length: Int = 10): String =
      val nchars: Int = (length * (percent / 100)).ceil.toInt
      "[ " + "#".repeat(nchars) + "-".repeat(length - nchars) + s" ] (${percent}%)"

    val workIterator = workGenerator(workset).iterator

    // val workIterator = workset.iterator

    val workQueue: Queue[Array[Array[Map[String, Double]]]] = Queue(workIterator.next)

    var dispatchResults: Seq[DispatchResult[JuliaDimensionResult]] = List()
    while (!workQueue.isEmpty)
      val work = workQueue.removeLast()
      logger.atInfo().log(s"Workset size: ${work.length}")

      val batches = partition(
        work,
        params.partitions
      )

      logger.info("<< Batch Allocations >>")
      val tBatchSize = batches.map((i) => i.length).sum
      logger.atInfo.log(f"Total number of units: ${tBatchSize.toString}")
      (0 to batches.length - 1).foreach { i =>
        logger.atInfo.log(s"Batch $i: ${getProgressBar(batches(i).length.toFloat / tBatchSize * 100)}")
      }
      logger.info("~".repeat(15))

      // todo, the batches need to be now be subdivided and looped over.
    
      var lot = 0

      // var centreDimensionMap: scala.collection.mutable.Map[String, JuliaDimensionResult] = scala.collection.mutable.Map()
      def dispatch(batch: Array[Map[String, Double]], number: Int): Future[DispatchResult[JuliaDimensionResult]] =
        val topic = s"${params.topicPrefix}-${number}"

        Future {
          val kafkaProducer = producerFactory(s"transaction-$number")
          batch.grouped(100).foreach(grp => {
            kafkaProducer.beginTransaction

            grp.foreach(entry => {
              // print(".")
              kafkaProducer.send(
                new ProducerRecord[String, String](
                  topic,
                  "coordinate",
                  Consumer.encodedPayload(
                      "%f".format(entry.getOrElse("r", 0)),
                      "%f".format(entry.getOrElse("i", 0)),
                      params.iterations.toString(),
                      runUUID.toString()
                    )
                  )
                ).get()
              })
              // print("|")
            kafkaProducer.commitTransaction()
          })
          kafkaProducer.close
          DispatchResult(
            message = s"Sent ${batch.length} entries to topic: $topic)",
            juliaDimensionResult = params match
              case p: ProducerParamsV2 if p.usingJuliaPolicy => {
                val ctr = Producer.juliaCentre(batch)
                JuliaDimensionResult(
                  getJuliaDimension(ctr, p.iterations, p.neighbourhoodSize),
                  ctr
                )
              }
              case _ => JuliaDimensionResult(-1, null)
          )
        }
        
      logger.info("Dispatching...") 
      val dispatchFuture = (0 to batches.length - 1).map(i => dispatch(batches(i), i))
      dispatchResults = Await.result(Future.sequence(dispatchFuture), scala.concurrent.duration.Duration.Inf)
      
      // **** Load Balancing ****
      params match
        case p if p.usingPerformancePolicy =>
          // wait for a set time interval or ascertain that alk the workers have finished.
          logger.info(s"Waiting for ${p.policy.performancePolicy.tryIntervalSec} before evaluating performance")
          Thread.sleep(p.policy.performancePolicy.tryIntervalSec * 1000)
          // evaluate performance here
          val lastPerformance = PerformanceEvaluator.getLastPerformance(params)
          // PerformanceEvaluator.orderByPerformance(lastPerformance)
          // rebalance here
          if (workIterator.hasNext)
            logger.info(s"Rebalanced work weights across the cluster, submitting new work request")
            workQueue.append(PerformanceEvaluator.rebalance(
              workIterator.next, 
              weights = PerformanceEvaluator.orderByPerformance(lastPerformance),
              nodePartitionMap = (0 to 15).map(i => (p.monitor.registeredConsumerNameTemplate.format(i), i)).toMap
            ))
            logger.info(s"Work request submitted")
        case _ =>
          if (workIterator.hasNext)
            workQueue.append(workIterator.next)
          // i.e. do not rebalance, continue straight on.
        
        // rebalance the work according to a chosen strategy (e.g. could be frame by frame based action of Julia set based optimation)
    // ---- end of perf-eval loop ----
    logger.info("Dispatch complete")
    dispatchResults.toList


  def produceGridPoints(frameConfigFile: String, kafkaProducer: KafkaProducer[String, String] = null) = {
    // fixed to support only ProducerParamsV2 for now.
    val params = Producer.getParams(frameConfigFile).asInstanceOf[ProducerParamsV2]
    
    // initial run
    if (params.isInstanceOf[ProducerParamsV2])
      var result = gridWorkStream(
        (transactionId: String) => {
          val producer = initProducer(transactionId)
          producer.initTransactions
          producer
        },
        params
      )

      // when using the juliaPolicy, the entire work stream needs to be re-run
      if (params.usingJuliaPolicy)
        var runsRemaining = params.policy.juliaPolicy.maxTries.toInt
        while runsRemaining > 0 do
          logger.info(s"Re-running: ${runsRemaining - 1 } run/s to go...")
          val policy = rrt.policy.Julia(params, result)
          val rerunParams = policy.onPostDispatchEvaluation()
          result = gridWorkStream(
            (transactionId: String) => {
              val producer = initProducer(transactionId)
              producer.initTransactions
              producer
            },
            params
          )

          runsRemaining -= 1

      logger.info("All done!")

    // evaluate performance (cwt)
    // 1. Connect to prometheus load balancer and extract statistics (e.g. run the dwell time query as grafana does - maybe grafana api may assist)
    // 2. determine which highest activity nodes vs lower activity nodes

    // choose strategy (e.g. mid-point julia vs load balance classification)
    // 3. Classify the performance by linking it to the julia mid point mapping, record this as in future the strategy to be chosen can be predetermined for example
    // 4. At this point, given the node activity (intensity is known), choose how to redistribute traffic (e.g. we can average out load by lowering work on highest activity nodes by redistributing their work to low activity nodes)

    // resubmit until policy is satisfied.
    // 5. run gridWorkStream again but partition the nodes according to step 4
    // 6. repeat steps 1 - 5 until a policy has been satisified (end-time or end-state).
  }
}
