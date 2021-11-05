#!/bin/bash


cd .
bin/zookeeper-server-start.sh config/zookeeper.properties &
bin/kafka-server-start.sh config/server.properties &

# wait for kafka
while [[ $(nc -z localhost 9092; echo $?) -ne 0 ]]; do echo $?; sleep 1; done;

# add kafka topic(s)
for i in {0..15}
do
	bin/kafka-topics.sh --zookeeper 0.0.0.0:2181  --create --if-not-exists --topic test-topic-$i --replication-factor=1  --partitions=1
done

# >> loop to wait for termination. <<
while true; do sleep 1; done;

