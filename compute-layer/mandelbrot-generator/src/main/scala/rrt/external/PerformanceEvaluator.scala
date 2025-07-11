package rrt.external

import java.time.Period
import io.prometheus.client.Summary
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.HttpResponse
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import scala.concurrent.Promise
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import rrt.ProducerParamsV2
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import com.fasterxml.jackson.databind.ObjectMapper
import scala.jdk.CollectionConverters._
import com.fasterxml.jackson.databind.node.ArrayNode
import rrt.model.wavelet.Morlet

// implicit val ec: ExecutionContext = ExecutionContext.global

given ExecutionContext = ExecutionContext.global
object PerformanceEvaluator:
    def getLastPerformance(params: ProducerParamsV2, windowMinutes: Int = 60): Map[String,Map[Double, Double]] =
        lazy val responseStr: Future[String] = Future {
            val formatter = DateTimeFormatter.ISO_INSTANT
            var endTime = ZonedDateTime.now()
            var startTime = endTime.minus(java.time.Duration.ofMinutes(windowMinutes))
            val uri = URI.create(
                params.monitor.prometheusApiUrl
                + "?query=" + URLEncoder.encode(s"${params.monitor.queries(0).query}")
                + s"&start=${URLEncoder.encode(formatter.format(startTime))}"
                + s"&end=${URLEncoder.encode(formatter.format(endTime))}"
                + "&step=60"
                )
            println(uri)
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(uri)
                .build()

            client.sendAsync(request, BodyHandlers.ofString())
                .thenApply((resp) => resp.body())
                .thenApply((data) => data)
                .join()
        }

        val data = Await.result(responseStr, Duration.Inf)
        val mapper = ObjectMapper()
        val document = mapper.readTree(data)
        // println("Status: " + document.at("/status").asText)
        // println("Result Metric: " + document.at("/data/result/0/metric/instance").asText)
        // println("Result Set: " + document.at("/data/result/0/values"))

        document.at("/data/result").asScala.map((result) =>
            val name: String = result.at("/metric/instance").asText
            val entries = document.at("/data/result/0/values").asScala.toList.map(e => (e.get(0).asDouble, e.get(1).asDouble)).toMap
            (name, entries)
        ).toMap

    /**
      * Descending order sort where the most busy node is on top.
      *
      * @param result
      */
    def orderByPerformance(result: Map[String, Map[Double, Double]]): List[Tuple] =
        result.map {case (k, v) => (k, v)}.toList.sortWith((a, b) => (a(1).values.sum / a(1).size) > (b(1).values.sum / b(1).size))
    
    /**
      * Rebalances the work load according to the peformance the last run (summarised with weights)
      * The entries in weights are arthimetic means which should aid in redistributing the work.
      *
      * @param work    a batch of work to rebalance
      * @param weights an ordered (highest performing/engaged at top) list of consumers
      * @return
      */
    def rebalance(work: Array[Array[Map[String, Double]]], weights: List[(String,Double)]): Array[Array[Map[String, Double]]] =
        // number of units are assumed to be initially evenly distributed in work, so take length of first
        val nUnits = work(0).length
    
        val wDist = List(weights(0)(1),weights.last(1)).reduce(_-_).abs

        val perfMean = weights.map {case (a, m) => (m / wDist) * nUnits}
        val avgPerf = perfMean.sum / perfMean.length

        // rebalance the top 50%
        (BigDecimal(1) to BigDecimal(perfMean.length * 0.5) by BigDecimal(1)).map{i => i.toInt}.map{i =>
            /* TODO: work should be in the same order as the sorted weights */
            val nFreeSlots = work(i).length * (perfMean(i) - avgPerf)
            (work(i).toList ::: work(work.length - i).slice(0, nFreeSlots.ceil.toInt).toList).toArray
        }.toArray
    
    /**
      * Performs wavelet transform to obtain a compressed, queriable representation of the entire time series
      * The results of this can be stored in a memory / datastore to forecast best node repositioning.
      * 
      * This step can be performed with or without orderByPerformance.
      * 
      * A base classifer (Mandelbrot) can be use for comparison.
      *
      * @param result
      * @return
      */
    def classifySignal(timeSeries: Array[Double]): Unit =
        val wavelet = Morlet()
        Morlet.convolve(timeSeries, wavelet.getMotherWavelet(bins = timeSeries.length, param = 5.5).toArray)
        None

