package rrt.external

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Period
import org.scalatest.Tag
import rrt.Producer
import rrt.ProducerParamsV2

object ExternalHttp extends Tag("rrt.external.Http")
object Rebalance extends Tag("rrt.external.Rebalance")

class PerformanceEvaluatorSpec extends AnyFlatSpec with Matchers {
    val params = Producer.getParams(System.getProperty("user.dir") + "/src/test/resources/test-work.yml").asInstanceOf[ProducerParamsV2]

    "PerformanceEvaluator" should "be able to request over http" taggedAs(ExternalHttp) in {
        val rawData = PerformanceEvaluator.getLastPerformance(params)
        assert(rawData.keys.toSet.contains("consumer-0:9091"))
        PerformanceEvaluator.orderByPerformance(rawData)
    }

    "PerformanceEvaluator" should "rebalancer work from the busiest node to the least busiest node proportionally" taggedAs(Rebalance) in {
        val workLoadA = Array(
            Array(Map("i" -> 1d),Map("i" -> 2d),Map("i" -> 3d),Map("i" -> 4d)),
            Array(Map("i" -> 5d),Map("i" -> 6d),Map("i" -> 7d),Map("i" -> 8d)),
            Array(Map("i" -> 9d),Map("i" -> 10d),Map("i" -> 11d),Map("i" -> 12d)),
            Array(Map("i" -> 13d),Map("i" -> 14d),Map("i" -> 15d),Map("i" -> 16d)),
        )

        val workIterator = Array(workLoadA).iterator

        val lastPerformance = Map(
            "a" -> Map(0.0 -> 100d),
            "b" -> Map(0.0 -> 100d),
            "c" -> Map(0.0 -> 100d),
            "d" -> Map(0.0 -> 0d),
        )

        val workConfiguration = PerformanceEvaluator.rebalance(
            workIterator.next,
            weights = PerformanceEvaluator.orderByPerformance(lastPerformance),
            nodePartitionMap = Map(
                "a" -> 0,
                "b" -> 1,
                "c" -> 2,
                "d" -> 3
            ),
        )
        workConfiguration.toList.foreach(i => println(i.toList))
    }
}
