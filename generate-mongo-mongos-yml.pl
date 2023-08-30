#!/usr/bin/perl

package main;

my $id = undef;
my $in = undef;
my $out = undef;

our $writeTo = undef;
foreach $arg (@ARGV) {
    if ($arg =~ /--id/) {
        $writeTo = \$id;
        next;
    } elsif ($arg =~ /--out/) {
        $writeTo = \$out;
        next;
    }


    if ($writeTo) {
        $$writeTo = $arg;
        $writeTo = undef;
    }
}

$out = $out or $out = "out.yml";

open FO, ">$out";

foreach $line (<DATA>) {
    $line =~ s/%ID%/$id/g;
    if ($line =~ /\{!import (.*?)\}$/) {
        open CI, "<$1";
        foreach $cert_line (<CI>) {
            print FO "    $cert_line";
        }
        close CI;
    } elsif ($line =~ /\{!decrypt (.*?)\}/) {
        $content = `openssl rsa -in $1 -passin pass:xiec.gate.r`;
        foreach $line (split /\n/, $content) {
          print FO "    $line\n";
        }
    } else {
        print FO $line;
    }
}

close FO;

__DATA__
# This file has been generated dynamically using generate-mongo-mongos-yml.pl, edit that file and re-run.
kind: ConfigMap
apiVersion: v1
metadata:
  name: startup-config
  namespace: dev
data:
  start.sh: |
    echo "Initialising Mongodb with default login users..."
    mongod --bind_ip_all --logpath '/var/log/mongo-initial-boot.log' &
    sleep 3
    mongosh --norc /kickstart/add-user.js
    kill -9 $(ps aux | grep mongod | grep bind_ip_all | awk '{print $2}')

    echo "Starting Mongos..."
    mongos --tlsAllowInvalidCertificates --tlsMode requireTLS --tlsCAFile /kickstart/root-ca.pem --tlsCertificateKeyFile /kickstart/mongos-1-svc.pem --tlsDisabledProtocols=none --configdb 'configServerRS/config-server-svc-1:27019,config-server-svc-2:27019,config-server-svc-3:27019' --clusterAuthMode x509 --bind_ip_all --logpath '/var/log/mongos.log'

    #mongos --configdb 'configServerRS/config-server-svc-1:27019,config-server-svc-2:27019,config-server-svc-3:27019' --bind_ip_all --logpath '/var/log/mongos.log'

    echo "Boot up complete"

  add-user.js: |
    // wait for mongod to be present...
    var conn;

    while(true) {
      try {
          conn = new Mongo('localhost:27017');
        break;
      } catch (Error) {
        print("Caught error!");
        print(Error);
      }
      sleep(100);
    }

    //conn.setReadPref('primary');
    print("Setting up mongdb users...");

    db = conn.getDB('admin').getSiblingDB('$external');

    var outcome = [];
    outcome.push(
        db.runCommand(
        {
          createUser: "OU=School of Computing and Mathematical Sciences,O=Birkbeck,emailAddress=rtimme01@bbk.ac.uk,C=GB,ST=Essex,CN=mongo-config-svc-1",
          roles: [
            {role: 'userAdminAnyDatabase', db: 'admin'},
            {role: 'readWrite', db: 'test'},
            {role: 'clusterAdmin', db: 'admin'}
          ],
          writeConcern: {
            w: 'majority',
            wtimeout: 5000
          }
        }
      )
    );

    outcome.push(
      db.runCommand(
        {
          createUser: "OU=SCMS,O=Birkbeck,emailAddress=rtimme01@bbk.ac.uk,C=GB,ST=Essex,CN=mongos-1-svc",
          roles: [
            {role: 'userAdminAnyDatabase', db: 'admin'},
            {role: 'readWrite', db: 'test'},
            {role: 'readWrite', db: 'ycsb'},
            {role: 'clusterAdmin', db: 'admin'}
          ],
          writeConcern: {
            w: 'majority',
            wtimeout: 5000
          }
        }
      )
    );

    outcome.push(
      db.getSiblingDB('$external').runCommand(
        {
          createUser: "OU=SCMS,O=Birkbeck,emailAddress=rtimme01@bbk.ac.uk,C=GB,ST=Essex,CN=localhost",
          roles: [
            {role: 'readWrite', db: 'test'},
            {role: 'readWrite', db: 'mandelbrot'}
          ],
          writeConcern: {
            w: 'majority',
            wtimeout: 5000
          }
        }
      )
    );

    outcome.push(
      db.runCommand(
        {
          createUser: "OU=SCMS,O=Birkbeck,emailAddress=rtimme01@bbk.ac.uk,C=GB,ST=Essex,CN=arnold-pri",
          roles: [
            {role: 'userAdminAnyDatabase', db: 'admin'},
            {role: 'readWrite', db: 'test'},
            {role: 'clusterAdmin', db: 'admin'}
          ],
          writeConcern: {
            w: 'majority',
            wtimeout: 5000
          }
        }
      )
    );

    printjson(outcome);
  mongos-sharding.js: |
    use admin;


  root-ca.pem: |
    {!import security/cacert.pem}
  mongos-1-svc.pem: |
    {!import security/certs/mongos-1-svc.pem}
    {!decrypt security/mongos-1-svc-key.pem}
  config-sever-1.pem: |
    {!import security/certs/config-server-svc-1.pem}
    {!decrypt security/config-server-svc-1-key.pem}
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: mongos-1
  namespace: dev
  labels:
    purpose: database
spec:
  replicas: 1
  selector:
    matchLabels:
      role:  mongos
      purpose: database
  template:
    metadata:
      labels:
        role: mongos
        purpose: database
    spec:
      containers:
        - name: mongos-1-pod
          image: localhost:32000/mongo-cst:1.0
          imagePullPolicy: Always
          command:
            - 'bin/sh'
            - '/kickstart/start.sh'
          volumeMounts:
            - name: kickstart-dir
              mountPath: /kickstart/
      volumes:
        - name: kickstart-dir
          configMap:
            name: startup-config
      # nodeSelector:
        # kubernetes.io/hostname: rrt-general-services
      affinity:
        nodeAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            nodeSelectorTerms:
              - matchExpressions:
                - key: role
                  operator: In
                  values:
                    - mongo-shard-1
                    - debugger

        

---
kind: Service
apiVersion: v1
metadata:
  name: mongos-1-svc
  namespace: dev
  labels:
    purpose: database
spec:
  selector:
    role: mongos
  ports:
    - port: 27017
      targetPort: 27017
  type: LoadBalancer
