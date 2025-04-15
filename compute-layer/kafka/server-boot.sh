#!/bin/bash


cd /var/kafka

export KAFKA_CLUSTER_ID="$(bin/kafka-storage.sh random-uuid)"
bin/kafka-storage.sh format --standalone -t $KAFKA_CLUSTER_ID -c config/server.properties
bin/kafka-server-start.sh config/server.properties &

# wait for kafka
while [[ $(nc -z localhost 9092; echo $?) -ne 0 ]]; do echo $?; sleep 1; done;

# add kafka topic(s)
for i in {0..15}
do
	bin/kafka-topics.sh --bootstrap-server 0.0.0.0:9092 --create --if-not-exists --topic test-$i --replication-factor=1 --partitions=16
done
bin/kafka-topics.sh --bootstrap-server 0.0.0.0:9092 --create --if-not-exists --topic system --replication-factor=1 --partitions=1
bin/kafka-topics.sh --bootstrap-server 0.0.0.0:9092 --create --if-not-exists --topic result --replication-factor=1 --partitions=16
bin/kafka-topics.sh --bootstrap-server 0.0.0.0:9092 --create --if-not-exists --topic loadjar --replication-factor=1 --partitions=16

# >> loop to wait for termination. <<
while true; do sleep 1; done;

