package rrt.external

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Period
import org.scalatest.Tag
import rrt.Producer
import rrt.ProducerParamsV2

object ExternalHttp extends Tag("rrt.external.http")

class PerformanceEvaluatorSpec extends AnyFlatSpec with Matchers {
    val params = Producer.getParams(System.getProperty("user.dir") + "/src/test/resources/test-work.yml").asInstanceOf[ProducerParamsV2]

    "PerformanceEvaluator" should "be able to request over http" in {
        val rawData = PerformanceEvaluator.getLastPerformance(params)
        assert(rawData.keys.toSet.contains("consumer-0:9091"))

        PerformanceEvaluator.orderByPerformance(rawData)
    }
}
