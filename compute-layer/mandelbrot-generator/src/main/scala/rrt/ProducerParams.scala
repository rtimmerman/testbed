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
)

@JsonIgnoreProperties(ignoreUnknown = true)
case class ProducerParamsV2 (
    @JsonProperty("version", required=true) version: Int,
    @JsonProperty("iterations", required=true) iterations: Int,
    @JsonProperty("topicPrefix", required=true) topicPrefix: String,
    @JsonProperty("coordinate", required=false) coordinate: String,
    @JsonProperty("neighbourhoodSize", required=false) neighbourhoodSize: Int,
    @JsonProperty("zoomPc", required=false) zoomPc: Int,
)
