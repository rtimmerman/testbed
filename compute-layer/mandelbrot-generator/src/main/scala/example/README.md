# Introduction

This document aims to outline usage of this distributed fractal suite.

Here is a high-level overview of the current algorithm.

```
(Shell) ---> [producer] ----> [consumer] ----> [data writer] ----> (Data Source)
```


# Synopsis

The overarching methodology surrounds the paritioning of work across the complex plane being convoluted with a fractal attractor such as the Mandelbrot set (the current default algorithm)

The basic command resembles this form

```scala
sbt "run <ROLE> [OPTIONS]"
```

The currently supported `<ROLE>`s are covered in the sections below.

## Producer

The producer submits work to a **topic** and has the following form:

```scala
sbt "run producer <topic> <interations>"
```

Where `<topic>` can be any legimately named Apache Kafka topic (for example `test`).

The producer will split the work automatically into sub topics.  For example, the command above will actually send work to 16 `<topic>` partitions.

## Consumer

The consumer carries out processing the topic allocated to it. It has this form:

```scala
sbt "run consumer <topic>"
```

Here, each consumer must subscribe to any of the partitions the producer sent work to. For example, if the producer sent work to a topic named `test` then at least one consumer should subscribe to the `test-0` topic.

## Data-Writer

```scala
sbt "run data-writer <data-topic>"
```

The data writer is similiar to a consumer apart from that rather than processing data in terms of the algorithm it shuttles results from the data-topic to the data store.

## Console

This project supports a console, it may be accessed like so:

```
java -jar assembly-jar-name.jar example.mandelbrot console
```

## Misc

This project supports fat-jar production using `sbt assembly`.  If this done, then instead of running `sbt run` each time.  Use the jar instead - for example,
```sh
# analogous to but faster than `sbt 'run producer test 1000'`
java -jar assembly-jar-name.jar example.mandelbrot producer test 1000
```
