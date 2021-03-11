#!/usr/bin/perl
$|=1;
package main;

use POSIX qw(strftime);

chdir('/YCSB');


sub get_timestamp
{
	return POSIX::strftime("%Y/%m/%d %H:%M:%S", localtime);
}

sub clear_db
{
	$clearDb = `mongo --tls --tlsCAFile /credstore/root-ca.pem --tlsCertificateKeyFile /credstore/mongos-1-svc.pem --authenticationDatabase '\$external' --authenticationMechanism='MONGODB-X509' mongos-1-svc:27017 --eval 'db.getSiblingDB("ycsb").dropDatabase()'`;

	print $clearDb . "\n";
	print "** DB Cleared **\n";
}

sub run_experiment_load
{
	$loadRun = `java -Djavax.net.ssl.keyStore=/credstore/mongodb.pkcs12 -Djavax.net.ssl.keyStorePassword=xiec.gate.r -Djavax.net.ssl.trustStore=/credstore/mongodb.pkcs12 -Djavax.net.ssl.trustStorePassword=xiec.gate.r -cp /YCSB/core/target/core-0.17.0.jar:/ycsb-mongo-sharded-db/ycsb-mongo-sharded-db/target/classes/:/YCSB/mongodb/target/mongodb-binding-0.17.0.jar::/YCSB/mongodb/target/dependency/mongo-java-driver-3.8.0.jar:/YCSB/lib/jackson-core-asl-1.9.4.jar:lib/jackson-mapper-asl-1.9.4.jar:/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/2.3.2/jakarta.xml.bind-api-2.3.2.jar:/.m2/repository/org/apache/htrace/htrace-core4/4.1.0-incubating/htrace-core4-4.1.0-incubating.jar:/.m2/repository/org/hdrhistogram/HdrHistogram/2.1.4/HdrHistogram-2.1.4.jar site.ycsb.Client -s -P /YCSB/workloads/workloadc -p mongodb.url="mongodb://mongos-1-svc:27017/ycsb?ssl=true&authMechanism=MONGODB-X509&authSource=\\\$external" -load -db uk.ac.bbk.mongoycsb.ShardedMongo`;
	print $loadRun;

	print $distributionStats . "\n";

	$distributionStats = `mongo --tls --tlsCAFile /credstore/root-ca.pem --tlsCertificateKeyFile /credstore/mongos-1-svc.pem --authenticationDatabase '\$external' --authenticationMechanism='MONGODB-X509' mongos-1-svc:27017 --eval 'db.getSiblingDB("ycsb").usertable.getShardDistribution()'`;

	print $distributionStats . "\n";

}

sub run_experiment_test
{
	$loadRun = `java -Djavax.net.ssl.keyStore=/credstore/mongodb.pkcs12 -Djavax.net.ssl.keyStorePassword=xiec.gate.r -Djavax.net.ssl.trustStore=/credstore/mongodb.pkcs12 -Djavax.net.ssl.trustStorePassword=xiec.gate.r -cp /YCSB/core/target/core-0.17.0.jar:/ycsb-mongo-sharded-db/ycsb-mongo-sharded-db/target/classes/:/YCSB/mongodb/target/mongodb-binding-0.17.0.jar::/YCSB/mongodb/target/dependency/mongo-java-driver-3.8.0.jar:/YCSB/lib/jackson-core-asl-1.9.4.jar:lib/jackson-mapper-asl-1.9.4.jar:/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/2.3.2/jakarta.xml.bind-api-2.3.2.jar:/.m2/repository/org/apache/htrace/htrace-core4/4.1.0-incubating/htrace-core4-4.1.0-incubating.jar:/.m2/repository/org/hdrhistogram/HdrHistogram/2.1.4/HdrHistogram-2.1.4.jar site.ycsb.Client -s -P /YCSB/workloads/workloadc -p mongodb.url="mongodb://mongos-1-svc:27017/ycsb?ssl=true&authMechanism=MONGODB-X509&authSource=\\\$external" -t -db uk.ac.bbk.mongoycsb.ShardedMongo`;

	print $testRun . "\n";
}

$repeats = 1;
$loadSleep = 1;
$exercise = 1;
$exerciseSleep = 1;

for $arg (@ARGV) {
	print $arg;
	$repeats = $1 if ($arg =~ /--repeats=(\d+)$/);
	$loadSleep = $1 if ($arg =~ /--load-sleep=(\d+)$/);
	$exercise = $1 if ($arg =~ /--exercise=(\d+)$/);
	$exerciseSleep = $1 if ($arg =~ /--exercise-sleep=(\d+)$/);
}

print "Experiment Starting at " . &::get_timestamp() . "\n";

for ($i = 0; $i < $repeats; ++$i) {
	print ">>> Run no. " . $i + 1 . " of $repeats\n";
	print ">>> Loading data at " . &::get_timestamp() . "\n";
	&::clear_db();
	&::run_experiment_load();
	if ($exercise) {
		print ">>> Testing data at " . &::get_timestamp() . "\n";
		print "Test will start in $exerciseSleep second(s).\n";
		sleep $exerciseSleep;
		&::run_experiment_test();
	}
	print ">>> Run finsihed at: " . &::get_timestamp() . "\n";
	print "Cooling down (sleep) for $loadSleep second(s)\n";
	sleep $loadSleep;
}

print "Experiment Ended at " . &::get_timestamp() . "\n";
1;
