package rrt

import io.prometheus.client.Counter

object RrtMetrics:
    val dwellTimeCounter: Counter = Counter.build()
        .name("dwell_time")
        .help("Work")
        .register()

    def appendDwellTime(amount: Long) =
        dwellTimeCounter.inc(amount)
