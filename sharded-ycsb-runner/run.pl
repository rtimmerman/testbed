#!/usr/bin/perl

package main;

use POSIX qw(strftime);

chdir('/home/roderick/workspace/YCSB');


sub get_timestamp
{
	return POSIX::strftime("%Y/%m/%d %H:%M:%S", localtime);
}

sub clear_db
{
	$clearDb = `mongo --tls --tlsCAFile /home/roderick/workspace/mongo-cluster/root-ca.pem --tlsCertificateKeyFile /home/roderick/workspace/mongo-cluster/mongos-1-svc.pem --authenticationDatabase '\$external' --authenticationMechanism='MONGODB-X509' mongos-1-svc:27017 --eval 'db.getSiblingDB("ycsb").dropDatabase()'`;

	print $clearDb . "\n";
	print "** DB Cleared **"
}

sub run_experiment_load
{
	$loadRun = `java -Djavax.net.ssl.keyStore=/home/roderick/workspace/mongo-cluster/ycsb-0.5.0/mongodb.pkcs12 -Djavax.net.ssl.keyStorePassword=xiec.gate.r -Djavax.net.ssl.trustStore=/home/roderick/workspace/mongo-cluster/ycsb-0.5.0/mongodb.pkcs12 -Djavax.net.ssl.trustStorePassword=xiec.gate.r -cp core/target/core-0.17.0.jar:../mongo-cluster/sharded-ycsb-runner/ycsb-mongo-sharded-db/target/classes/:mongodb/target/mongodb-binding-0.17.0.jar:/home/roderick/.m2/repository/org/mongodb/mongo-java-driver/3.12.7/mongo-java-driver-3.12.7.jar:lib/jackson-core-asl-1.9.4.jar:lib/jackson-mapper-asl-1.9.4.jar:/home/roderick/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/2.3.2/jakarta.xml.bind-api-2.3.2.jar:/home/roderick/.m2/repository/org/apache/htrace/htrace-core4/4.1.0-incubating/htrace-core4-4.1.0-incubating.jar:/home/roderick/.m2/repository/org/hdrhistogram/HdrHistogram/2.1.4/HdrHistogram-2.1.4.jar site.ycsb.Client -s -P workloads/workloada -p mongodb.url="mongodb://mongos-1-svc:27017/ycsb?ssl=true&authMechanism=MONGODB-X509&authSource=\\\$external" -load -db uk.ac.bbk.mongoycsb.ShardedMongo`;

	print $distributionStats . "\n";

	$distributionStats = `mongo --tls --tlsCAFile /home/roderick/workspace/mongo-cluster/root-ca.pem --tlsCertificateKeyFile /home/roderick/workspace/mongo-cluster/mongos-1-svc.pem --authenticationDatabase '\$external' --authenticationMechanism='MONGODB-X509' mongos-1-svc:27017 --eval 'db.getSiblingDB("ycsb").usertable.getShardDistribution()'`;

	print $distributionStats . "\n";

}

sub run_experiment_test
{
	$testRun = `java -Djavax.net.ssl.keyStore=/home/roderick/workspace/mongo-cluster/ycsb-0.5.0/mongodb.pkcs12 -Djavax.net.ssl.keyStorePassword=xiec.gate.r -Djavax.net.ssl.trustStore=/home/roderick/workspace/mongo-cluster/ycsb-0.5.0/mongodb.pkcs12 -Djavax.net.ssl.trustStorePassword=xiec.gate.r -cp core/target/core-0.17.0.jar:../mongo-cluster/sharded-ycsb-runner/ycsb-mongo-sharded-db/target/classes/:mongodb/target/mongodb-binding-0.17.0.jar:/home/roderick/.m2/repository/org/mongodb/mongo-java-driver/3.12.7/mongo-java-driver-3.12.7.jar:lib/jackson-core-asl-1.9.4.jar:lib/jackson-mapper-asl-1.9.4.jar:/home/roderick/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/2.3.2/jakarta.xml.bind-api-2.3.2.jar:/home/roderick/.m2/repository/org/apache/htrace/htrace-core4/4.1.0-incubating/htrace-core4-4.1.0-incubating.jar:/home/roderick/.m2/repository/org/hdrhistogram/HdrHistogram/2.1.4/HdrHistogram-2.1.4.jar site.ycsb.Client -s -P workloads/workloada -p mongodb.url="mongodb://mongos-1-svc:27017/ycsb?ssl=true&authMechanism=MONGODB-X509&authSource=\\\$external" -t -db uk.ac.bbk.mongoycsb.ShardedMongo`;

	print $testRun . "\n";
}

$repeats = 1;
$loadSleep = 1;
$exercise = 1;
$exerciseSleep = 1;

for $arg (@ARGV) {
	$repeats = $1 if ($arg =~ /--repeats=(\d+)$/);
	$loadSleep = $1 if ($arg =~ /--load-sleep=(\d+)$/);
	$exercise = $1 if ($arg =~ /--exercise=(\d+)$/);
	$exerciseSleep = $1 if ($exerciseSleep =~ /--exercise-sleep=(\d+)$/);
}

print "Experiment Starting at " . &::get_timestamp() . "\n";

for ($i = 0; $i < $repeats; ++$i) {
	print ">>> Loading data at " . &::get_timestamp() . "\n";
	&::clear_db();
	&::run_experiment_load();
	if ($exercise) {
		print ">>> Testing data at " . &::get_timestamp() . "\n";
		print "Test will start in $exceriseSleep second(s).\n";
		sleep $exerciseSleep;
		&::run_experiment_test();
	}
	print "Run finsihed at: " . &::get_timestamp() . "\n";
	print "Sleeping for $loadSleep second(s)\n";
	sleep $loadSleep;
}

print "Experiment Ended at " . &::get_timestamp() . "\n";
1;
