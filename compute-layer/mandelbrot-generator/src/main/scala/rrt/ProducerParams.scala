package rrt

import com.fasterxml.jackson.annotation.{JsonProperty,JsonIgnoreProperties};

@JsonIgnoreProperties(ignoreUnknown = true)
case class ProducerParams(
    @JsonProperty("version", required=true) version: Int,
    @JsonProperty("iterations", required=true) iterations: Int,
    @JsonProperty("topicPrefix", required=true) topicPrefix: String,
    @JsonProperty("minI", required=false) minI: Double,
    @JsonProperty("maxI", required=false) maxI: Double,
    @JsonProperty("maxR", required=false) maxR: Double,
    @JsonProperty("minR", required=false) minR: Double,
    @JsonProperty("sizeX", required=false) sizeX: Int,
    @JsonProperty("sizeY", required=false) sizeY: Int,
    @JsonProperty("partitions", required=true) partitions: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
case class ProducerParamsV2 (
    @JsonProperty("version", required=true) version: Int,
    @JsonProperty("iterations", required=true) iterations: Int,
    @JsonProperty("topicPrefix", required=true) topicPrefix: String,
    @JsonProperty("coordinate", required=false) coordinate: String,
    @JsonProperty("neighbourhoodSize", required=false) neighbourhoodSize: Int,
    @JsonProperty("zoomPc", required=false) zoomPc: Int,
    @JsonProperty("policy", required=false) policy: ProducerWorkPolicy,
    @JsonProperty("monitor", required=false) monitor: Monitor,
    @JsonProperty("partitions", required=true) partitions: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
case class Monitor(
    @JsonProperty("prometheus_api_url") prometheusApiUrl: String,
    @JsonProperty("registered_consumer_name_template") registeredConsumerNameTemplate: String,
    @JsonProperty("queries") queries: Array[Queries]
)

@JsonIgnoreProperties(ignoreUnknown = true)
case class Queries(
    @JsonProperty("id") id: String,
    @JsonProperty("query") query: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
case class ProducerWorkPolicy(
    @JsonProperty("performance", required=false) performancePolicy: PerformancePolicy,
    @JsonProperty("julia", required = false) juliaPolicy: JuliaPolicy,
    @JsonProperty("none", required = false) nonePolicy: NonePolicy,
)

case class NonePolicy(
    @JsonProperty("active", required = true) active: Boolean = false,
    @JsonProperty("block_size", required = true) blockSize: Int
)

@JsonIgnoreProperties(ignoreUnknown = true)
case class PerformancePolicy(
    @JsonProperty("max_tries", required = true) maxTries: Int,
    @JsonProperty("max_eval_units", required = true) maxEvalUnits: Int,
    @JsonProperty("try_interval_sec", required = true) tryIntervalSec: Int,
)

enum TendMinMax(val s: String):
   case Min extends TendMinMax("min")
   case Max extends TendMinMax("max")

case class JuliaPolicy(
    @JsonProperty("max_tries", required = true) maxTries: Int,
    @JsonProperty("max_eval_units", required = true) maxEvalUnits: Int,
    @JsonProperty("try_interval_sec", required = true) tryIntervalSec: Int,
    @JsonProperty("dimension_tendency", required = true) dimTendency: TendMinMax
)

object ProducerParamsV2Extension:
    extension(p: ProducerParamsV2)
        def usingPerformancePolicy: Boolean = p.policy.performancePolicy != null
        def usingNoPolicy: Boolean = p.policy.nonePolicy != null
        def usingJuliaPolicy: Boolean = p.policy.juliaPolicy != null