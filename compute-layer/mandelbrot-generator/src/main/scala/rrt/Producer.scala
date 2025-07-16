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
    val size = space.length * space(0).length
    // for (x <- 0 to  Math.sqrt(nPartitions).toInt - 1; y <- 0 to Math.sqrt(nPartitions).toInt - 1)
      // println(s"Y:($y,$x) ${y * height} to ${(y * height) + height}, ${x * width} to ${(x * width) + width}")

    for (x <- 0 to  Math.sqrt(nPartitions).toInt - 1; y <- 0 to Math.sqrt(nPartitions).toInt - 1)
      yield space
        .slice((y * height), (y * height) + height + 1)
        .map(a => a.slice((x * width), (x * width) + width + 1))
        .flatten
  
  case class JuliaDimensionResult(dim: Double, centre: rrt.Complex)

  def getJuliaDimension(grp: Array[Map[String, Double]], iterations: Int, boxSize: Int): JuliaDimensionResult =
    val nums = grp.map(rrt.Complex.fromMap)
    val min = nums.reduce((a, b) => if a.r < b.r then a else b)
    val max = nums.reduce((a, b) => if a.i > b.i then a else b)
    val centre = rrt.Complex((min.r + max.r) / 2, (min.i + max.i) / 2) //<- find the midpoint using averages between real and imaginary parts separately
    logger.debug(f"calculating julia dimension for $centre")
    // now calculate the box count dimension the number of iterations will match the config
    var d = 0
    var blowup = 4.0

    val z_plane = (BigDecimal(-2) to BigDecimal(2) by BigDecimal(4.0/boxSize))
      .map((i) => (BigDecimal(-2) to BigDecimal(2) by BigDecimal(4.0/boxSize))
      .map((r) => rrt.Complex(r,i)))

    lazy val m: (rrt.Complex, Int) => Int = (z, n) =>{
      val z2 = (z ** 2) + centre
      // println(z2.abs())
      z2.abs() match
        case v if v >= blowup => iterations - n
        case v if n > 0 => m(z2, n - 1)
        case _ => -1
    
    }
    //         if (box_threshold <= area[int(x)][int(y)] or area[int(x)][int(y)] == -1):
    //        nr += 1

    val boxThreshold = 0.05 * iterations

    val julia = z_plane.flatten.map {case z =>
      val e = m(z, iterations) 
      e match
        case v if v == -1|| (v > 0 && v >= boxThreshold)  => 1
        case _ => 0
    }

    // ** Verification Plot **
    // julia.zipWithIndex.foreach {case (z, idx) =>
    //     if (z == 1) {
    //       print(idx match
    //         case n if n <= n % (boxSize / 4.0) => 1
    //         case _ => '*'
    //         )
    //     } else {
    //       print(' ')
    //     }
    //     if (idx % (boxSize + 1) == 0) {
    //       println()
    //     }
    //   }

    // val boxCount = 6
    // val divs = julia.toList.grouped(julia.size / Math.pow(boxCount, 2).toInt).map(g => g.sum).filter(n => n > 0)  
    // val nr = divs.size

    val avgBoxNr = Range(2, 5)
      .map {case boxCount => Map(
        "nr" -> julia.toList.grouped(julia.size / Math.pow(boxCount, 2).toInt).map(g => g.sum).filter(n => n > 0).size, //number of filled boxes
        "nb" -> boxCount, // box length (i.e. nb^2 = total number of boxes)
        )}
      .map {resmap => Math.abs(Math.log(resmap("nr")) / Math.log(1.0/Math.pow(resmap("nb"), 2)))}

    if (avgBoxNr.sum.isInfinity) {
      logger.debug(s"Setting dimension to 1.0 since the sum for ${centre} is infinity")
      return JuliaDimensionResult(1.0d, centre)
    }
    logger.info(s"Aquired julia dimension for ${centre}")

    JuliaDimensionResult(
      dim = BigDecimal(avgBoxNr.sum / avgBoxNr.size).setScale(2, scala.math.BigDecimal.RoundingMode.HALF_UP).toDouble,
      centre = centre
    )

  def getParams(configFilePath: String): ProducerParams | ProducerParamsV2 =
    var mapper = new ObjectMapper(new YAMLFactory())
    mapper.readValue(new File(configFilePath), classOf[ProducerParams]) match
      case p if p.version == 2 => mapper.readValue(new File(configFilePath), classOf[ProducerParamsV2])
      case p => p

  def gridWorkStream(
      producerFactory: (String) => (Producer[String, String]),
      params: ProducerParams | ProducerParamsV2
  ): Unit =
    val runUUID = UUID.randomUUID()

    var b: Array[Array[Map[String,Double]]] = null
    var space = 0

    params match
      case p: ProducerParamsV2 =>
        logger.info(s"Started run (UUID: $runUUID})")
        logger.info(s"Received v2 parameter set")
        // val params = mapper.readValue(new File(frameConfigFile), classOf[ProducerParamsV2]): ProducerParamsV2
        logger.info(params.getClass.toString)
        b = createSpaceFromPoint(rrt.Complex.fromString(p.coordinate), p.zoomPc, p.neighbourhoodSize)
        space = p.neighbourhoodSize * p.neighbourhoodSize
        logger.info(s"Run: (Zoom %: ${p.zoomPc} | initial entry: ${rrt.Complex.fromMap(b(0)(0))})")
      case p: ProducerParams =>
        logger.info(s"Received v1 parameter set")
        // val params = paramsBase
        b = createSpace(p.sizeX, p.sizeY, p.minR, p.maxR, p.minI, p.maxI)
        space = p.sizeX * p.sizeY

    val workset = b.map(_.sliding(100, 100).toArray)

    case class DispatchResult(message: String, juliaDimensionResult: JuliaDimensionResult)
    // perf-eval loop

    def workGenerator(workset: Array[Array[Array[Map[String,Double]]]]) =
      for (i <- Range(0, workset(0).toList.length))
        yield workset.map(_.toList(i).toArray).toArray

    val workIterator = workGenerator(workset).iterator

    // val workIterator = workset.iterator

    val workQueue: Queue[Array[Array[Map[String, Double]]]] = Queue(workIterator.next)
    while (!workQueue.isEmpty)
      val work = workQueue.removeLast()
      logger.atInfo().log(s"Workset size: ${work.length}")
      val batches = partition(work, 16)
      // todo, the batches need to be now be subdivided and looped over.
    
      var lot = 0

      // var centreDimensionMap: scala.collection.mutable.Map[String, JuliaDimensionResult] = scala.collection.mutable.Map()
      def dispatch(batch: Array[Map[String, Double]], number: Int): Future[DispatchResult] =
        val topic = params match
          case p: ProducerParamsV2 => s"${p.topicPrefix}-${number}"
          case p: ProducerParams => s"${p.topicPrefix}-${number}"

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
                      params match
                        case p: ProducerParamsV2 => p.iterations.toString()
                        case p: ProducerParams => p.iterations.toString(),
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
              // case p: ProducerParamsV2 => getJuliaDimension(batch, p.iterations, p.neighbourhoodSize)
              case _ => JuliaDimensionResult(-1, null)
          )
        }
        
      logger.info("Dispatching...")
      val dispatchFuture = (0 to batches.length - 1).map(i => dispatch(batches(i), i))
      Await.result(Future.sequence(dispatchFuture), scala.concurrent.duration.Duration.Inf)

      
      // **** Load Balancing ****
      params match
        case p: ProducerParamsV2 =>
          // wait for a set time interval or ascertain that alk the workers have finished.
          Thread.sleep(p.policy.stableRegionPolicy.tryIntervalSec * 1000)
          // evaluate performance here
          val lastPerformance = PerformanceEvaluator.getLastPerformance(params.asInstanceOf[ProducerParamsV2])
          PerformanceEvaluator.orderByPerformance(lastPerformance)
          // rebalance here
          if (workIterator.hasNext)
            workQueue.append(PerformanceEvaluator.rebalance(
              workIterator.next, 
              weights = PerformanceEvaluator.orderByPerformance(lastPerformance),
              nodePartitionMap = (0 to 15).map(i => (p.monitor.registeredConsumerNameTemplate.format(i), i)).toMap
            ))
        case _ =>
        
        // rebalance the work according to a chosen strategy (e.g. could be frame by frame based action of Julia set based optimation)
    // ---- end of perf-eval loop ----
    logger.info("Dispatch complete")

    /*
    params match
      // Julia Dimension based Optimisation Hypothesis Test (run by run)
      // let's test the hypothesis for julia, here is the re-entrant code for that
      case p: ProducerParamsV2 if p.policy.stableRegionPolicy != null && p.policy.stableRegionPolicy.maxTries > 0 =>
        logger.info("Julia re-positioning (hypothesis test)")
        val closestCentre = results.map {r => r.juliaDimensionResult}.reduce { (a, b) => if a.dim > b.dim then a else b }
        val p2 = p.copy(
          coordinate = closestCentre.centre.toString,
          policy = ProducerWorkPolicy(StableRegionPolicy(maxTries = p.policy.stableRegionPolicy.maxTries - 1, tryIntervalSec = p.policy.stableRegionPolicy.tryIntervalSec))
        )
        // wait before submitting work again.
        logger.info(s"Ready to send work for new coordinate ${p2.coordinate} (after ${p2.policy.stableRegionPolicy.tryIntervalSec} seconds)")
        Thread.sleep(p.policy.stableRegionPolicy.tryIntervalSec * 1000)
        gridWorkStream(producerFactory, p2)
      case _ =>
      */

    logger.info("Done")

  def produceGridPoints(frameConfigFile: String, kafkaProducer: KafkaProducer[String, String] = null) = {
    gridWorkStream((transactionId: String) => {
      val producer = initProducer(transactionId)
      producer.initTransactions
      producer
    }, Producer.getParams(frameConfigFile))

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
