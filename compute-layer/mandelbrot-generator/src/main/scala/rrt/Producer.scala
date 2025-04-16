package rrt
import java.util.{Properties, UUID}
import org.apache.kafka.clients.producer._
import breeze.math._
import breeze.linalg._

import java.security.MessageDigest;
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

import scala.concurrent.{Future, ExecutionContext, Await}
import scala.util.{Success, Failure}
import scala.util.Random

import org.slf4j.{Logger,LoggerFactory}

object Producer extends KafkaTrait {
  type Space = Array[Array[Map[String, Double]]];

  def createSpaceFromPoint(c: rrt.Complex, zoomPc: Integer = 100, ball: Integer = 1000, canvasDimensions: Map[String, Int] = Map("minR" -> -2, "maxR" -> 2, "minI" -> -2, "maxI" -> 2)): Space = {
    val scale = 1 / (zoomPc.toDouble / 100)
    val linspace = (x1: BigDecimal, x2: BigDecimal, count: BigDecimal) => (x1 to x2 by ((x1 - x2).abs / (count)))
    val R = linspace(canvasDimensions("minR"), canvasDimensions("maxR"), BigDecimal(ball))
    val I = linspace(canvasDimensions("minI"), canvasDimensions("maxI"), BigDecimal(ball))

    // the point is c

    // transform the points by adding c and multiplying them by scale across the initial space.
    val tR = R.map(e => (e + c.r) * scale)
    val tI = I.map(e => (e + c.i) * scale).reverse

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

    // for (x <- 0 to space(0).length - 1 by width; y <- 0 to space.length - 1 by height)
    //   yield space.slice(y, y + height + 1).map(a => a.slice(x, x + width + 1)).flatten

  def gridWorkStream(
      producerFactory: (String) => (Producer[String, String]),
      frameConfigFile: String
  ) =
    val runUUID = UUID.randomUUID()

    var mapper = new ObjectMapper(new YAMLFactory())
    var paramsBase = mapper.readValue(new File(frameConfigFile), classOf[ProducerParams]): ProducerParams
    var b: Array[Array[Map[String,Double]]] = null
    var space = 0

    if (paramsBase.version == 2)
        val params = mapper.readValue(new File(frameConfigFile), classOf[ProducerParamsV2]): ProducerParamsV2
        b = createSpaceFromPoint(rrt.Complex.fromString(params.coordinate), params.zoomPc, params.neighbourhoodSize)
        space = params.neighbourhoodSize * params.neighbourhoodSize
    else
        val params = paramsBase
        b = createSpace(params.sizeX, params.sizeY, params.minR, params.maxR, params.minI, params.maxI)
        space = params.sizeX * params.sizeY

    val batches = partition(b, 16)
  
    var lot = 0

    def dispatch(batch: Array[Map[String, Double]], number: Int): Future[String] =
      val topic = s"${paramsBase.topicPrefix}-${number}"
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
                    paramsBase.iterations.toString(),
                    runUUID.toString()
                  )
                )
              ).get()
            })
            // print("|")
          kafkaProducer.commitTransaction()
        })
        kafkaProducer.close

        s"Sent ${batch.length} entries to topic: $topic)"
      }
    
    logger.info("Dispatching...")
    val dispatchFuture = (0 to batches.length - 1).map(i => dispatch(batches(i), i))

    // dispatchFuture.foreach {future =>
    //   future.foreach {result => println(s"Result = $result")}}
   
    // wait for all dispatches to complete
    val results = Await.result(Future.sequence(dispatchFuture), scala.concurrent.duration.Duration.Inf)
    results.foreach(logger.info)
    logger.info("Done")

  def produceGridPoints(frameConfigFile: String, kafkaProducer: KafkaProducer[String, String] = null) = {
    gridWorkStream((transactionId: String) => {
      val producer = initProducer(transactionId)
      producer.initTransactions
      producer
    }, frameConfigFile)
  }
}
