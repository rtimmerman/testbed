package rrt

import com.fasterxml.jackson.annotation.JsonProperty;

case class ProducerParams(
    @JsonProperty("iterations") iterations: Int,
    @JsonProperty("topicPrefix") topicPrefix: String,
    @JsonProperty("minI") minI: Double,
    @JsonProperty("maxI") maxI: Double,
    @JsonProperty("maxR") maxR: Double,
    @JsonProperty("minR") minR: Double,
    @JsonProperty("sizeX") sizeX: Int,
    @JsonProperty("sizeY") sizeY: Int
)
