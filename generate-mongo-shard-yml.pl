#!/usr/bin/perl

package main;

my $id = undef;
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
# This file has been generated dynamically using generate-mongo-shard-yml.pl, edit that file and re-run.
apiVersion: v1
kind: ConfigMap
metadata:
  name: mongo-rs-config-%ID%
  namespace: dev
data:
  mongo-rs-config-%ID%.js: |
    while (true) {
      sleep(3000);
      var result = rs.initiate({
        _id: "%ID%.rs",
        members: [
          {_id: 0, host: "%ID%-pri:27018", priority: 2},
          {_id: 1, host: "%ID%-sec-1:27018", priority: 1},
          {_id: 2, host: "%ID%-sec-2:27018", priority: 1}
        ]
      });
      print("**OUTCOME**");
      printjson(result);

      if (result.ok == 1 || [23].includes(result.code)) {
        break;
      }
    }

  mongo-replication-%ID%.yml: |
    sharding:
      clusterRole:  shardsvr
    replication:
      replSetName: "%ID%.rs"
    security:
      clusterAuthMode: x509
    net:
      bindIp: "*"
      tls:
        mode: requireTLS
        certificateKeyFile: "/kickstart/%ID%-pri.pem"
        clusterFile: "/kickstart/config-server.pem"
        clusterCAFile: "/kickstart/root-ca.pem"
        CAFile: "/kickstart/root-ca.pem"
        allowInvalidHostnames: true
        allowInvalidCertificates: true
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: add-shard-%ID%
  namespace: dev
data:
  add-shard-%ID%.js: |
    var ok = false;

    while(!ok) {
      sleep(30000);
      var error = sh.addShard(
        "%ID%.rs/%ID%-pri:27018,%ID%-sec-1:27018,%ID%-sec-2:27018"
      );

      ok = (error.ok == 1);

      printjson(error);
    }

    print("installing cartToPol fn");

      var out = db.system.js.save({
        _id: 'cartToPol',
        value: function (x, y, width, height) {
            return {
                re: -2 + ((x / width) * 4),
                im: -2 + ((y / height) * 4)
            };
        }
      });

    printjson(out);

    print("installing mandelbrot fn");

    var out = db.system.js.save({
      _id: 'mandelbrot',
      value: function (z, c, iterations) {
          if (iterations == 0) {
              return -1;
          }

          var M = {
              re: ((z.re * z.re) - (z.im * z.im)) + c.re,
              im: (2 * (z.im * z.re)) + c.im
          }

          var M_abs = Math.sqrt((M.re * M.re) + (M.im * M.im));

          if (M_abs > 4.0) {
              return iterations;
          }

          return mandelbrot(M, c, iterations - 1);
      }
    });

    printjson(out);
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: kickstart-%ID%
  namespace: dev
data:
  app-support-pri.sh: |
    pip3 install psutil
    mkdir -p /filestore/data/%ID%-pri
    mongod --bind_ip_all &
    sleep 3
    mongosh --norc /kickstart/add-user.js
    kill -9 $(ps aux | grep mongod | grep bind_ip_all | awk '{print $2}')

    mongod --config /config/mongo-replication-%ID%.yml --bind_ip_all --dbpath /filestore/data/%ID%-pri &
    for i in {1..60}; do
       sleep 10
       echo 'waiting for %ID%-pri:27018 ...'
       if `nc -z %ID%-pri 27018`; then
        echo 'setting up mongo rs via %ID%-pri ...'
        mongosh --tls --tlsCertificateKeyFile /kickstart/%ID%-pri.pem --tlsCAFile /kickstart/root-ca.pem --authenticationDatabase '$external' --authenticationMechanism MONGODB-X509 %ID%-pri:27018 /config/mongo-rs-config-%ID%.js
        echo 'mongo rs configured!'
        break
      fi 
    done
    #python3 /filestore/monitor.py
    cd $HOME
    tar -zxf /filestore/node_exporter-1.0.1.linux-amd64.tar.gz
    ./node_exporter-1.0.1.linux-amd64/node_exporter --web.listen-address=0.0.0.0:9091
  app-support-sec-1.sh: |
    pip3 install psutil
    mkdir -p /filestore/data/%ID%-sec-1
    mongod --clusterAuthMode x509 --tlsMode requireTLS --tlsCertificateKeyFile /kickstart/%ID%-sec-1.pem --tlsCAFile /kickstart/root-ca.pem --shardsvr --replSet %ID%.rs --bind_ip_all --dbpath /filestore/data/%ID%-sec-1 &
    cd $HOME
    tar -zxf /filestore/node_exporter-1.0.1.linux-amd64.tar.gz
    ./node_exporter-1.0.1.linux-amd64/node_exporter --web.listen-address=0.0.0.0:9091
  app-support-sec-2.sh: |
    pip3 install psutil
    mkdir -p /filestore/data/%ID%-sec-2
    mongod --clusterAuthMode x509 --tlsMode requireTLS --tlsCertificateKeyFile /kickstart/%ID%-sec-2.pem --tlsCAFile /kickstart/root-ca.pem --shardsvr --replSet %ID%.rs --bind_ip_all --dbpath /filestore/data/%ID%-sec-2 &
    cd $HOME
    tar -zxf /filestore/node_exporter-1.0.1.linux-amd64.tar.gz
    ./node_exporter-1.0.1.linux-amd64/node_exporter --web.listen-address=0.0.0.0:9091
  configurator.sh: |
    for i in {1..60}; do
      sleep 1
      if nc -z mongos-1-svc 27017; then
        echo 'Establishing shards'
        mongosh --tls --tlsCertificateKeyFile /kickstart/mongos-1-svc.pem --tlsCAFile /kickstart/root-ca.pem --authenticationDatabase '$external' --authenticationMechanism MONGODB-X509 mongos-1-svc:27017/admin /kickstart-shard/add-shard-%ID%.js
        break
      fi
    done
  
  add-user.js: |
    // wait for mongod to be present...
    var conn;

    while(true) {
      try {
        conn = new Mongo('localhost:27017');
        break;
      } catch (Error) {
        print(Error);
      }
      sleep(100);
    }

    //conn.setReadPref('primary');

    db = conn.getDB('admin').getSiblingDB('$external');

    var outcome = [];
    outcome.push(
        db.runCommand(
        {
          createUser: "CN=%ID%-pri,OU=ExperimentClients,O=Roderick,O=BBK,L=Southminster,ST=Essex,C=GB",
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
          createUser: "CN=%ID%-sec-1,OU=ExperimentClients,O=Roderick,O=BBK,L=Southminster,ST=Essex,C=GB",
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
          createUser: "CN=%ID%-sec-2,OU=ExperimentClients,O=Roderick,O=BBK,L=Southminster,ST=Essex,C=GB",
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
          createUser: "CN=mongos-1-svc,OU=ExperimentServers,O=Roderick,O=BBK,L=Southminster,ST=Essex,C=GB",
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
  root-ca.pem: |
    {!import security/cacert.pem}
  mongos-1-svc.pem: |
    {!import security/certs/mongos-1-svc.pem}
    {!decrypt security/mongos-1-svc-key.pem}
  config-server.pem: |
    {!import security/certs/config-server-svc-1.pem}
    {!decrypt security/config-server-svc-1-key.pem}
  %ID%-pri.pem: |
    {!import security/certs/%ID%-pri.pem}
    {!decrypt security/%ID%-pri-key.pem}
  %ID%-sec-1.pem: |
    {!import security/certs/%ID%-sec-1.pem}
    {!decrypt security/%ID%-sec-1-key.pem}
  %ID%-sec-2.pem: |
    {!import security/certs/%ID%-sec-2.pem}
    {!decrypt security/%ID%-sec-2-key.pem}
    
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: %ID%-pri
  labels:
    role: %ID%-pri
    purpose: database
  namespace: dev
spec:
  selector:
    matchLabels:
      role: %ID%-pri
      purpose: database
  replicas: 1
  strategy:
    type: RollingUpdate
  template:
      metadata:
        labels:
          role: %ID%-pri
          purpose: database
      spec:
        terminationGracePeriodSeconds: 5
        volumes:
          - name: startup-config
            configMap:
              name: mongo-rs-config-%ID%
          - name: kickstart-%ID%
            configMap:
              name: kickstart-%ID%
              defaultMode: 0777
            #persistentVolumeClaim:
            #  claimName: filestore 
        containers:
          - image: localhost:32000/mongo-cst:1.0
            imagePullPolicy: Always
            name: mongo-db-container
            command:
              - "/bin/sh"
              - "-c"
              - "/kickstart/app-support-pri.sh"
            ports:
              - containerPort: 27018
              - containerPort: 9091
            volumeMounts:
              - name: startup-config
                mountPath: /config/
              - name: kickstart-%ID%
                mountPath: /kickstart/
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
apiVersion: apps/v1
kind: Deployment
metadata:
  name: %ID%-sec-1
  labels:
    role: %ID%-sec-1
    purpose: database
  namespace: dev  
spec:
  selector:
    matchLabels:
      role: %ID%-sec-1
      purpose: database
  replicas: 1
  strategy:
    type: RollingUpdate
  template:
    metadata:
      labels:
        role: %ID%-sec-1
        purpose: database
    spec:
      terminationGracePeriodSeconds: 5
      containers:
        - image: localhost:32000/mongo-cst:1.0
          imagePullPolicy: Always
          name: mongo-db-container
          ports:
            - containerPort: 27018
            - containerPort: 9091
          command:
            - "/bin/sh"
            - "-c"
            - "/kickstart/app-support-sec-1.sh"
          volumeMounts:
            - name: kickstart-%ID%
              mountPath: /kickstart/
            # - name: filestore
              # mountPath: /filestore/
      volumes:
        - name: kickstart-%ID%
          configMap:
            name: kickstart-%ID%
            defaultMode: 0777
        # - name: filestore
          # hostPath:
          #  path: /home/roderick/experiment-data
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
apiVersion: apps/v1
kind: Deployment
metadata:
  name: %ID%-sec-2
  labels:
    role: %ID%-sec-2
    purpose: database
  namespace: dev
spec:
  selector:
    matchLabels:
      role: %ID%-sec-2
      purpose: database
  replicas: 1
  strategy:
    type: RollingUpdate
  template:
    metadata:
      labels:
        role: %ID%-sec-2
        purpose: database
    spec:
      terminationGracePeriodSeconds: 5
      containers:
        - image: localhost:32000/mongo-cst:1.0
          imagePullPolicy: Always
          name: mongo-db-container
          ports:
            - containerPort: 27018
            - containerPort: 9091
          command:
            - "/bin/sh"
            - "-c"
            - "/kickstart/app-support-sec-2.sh"
          volumeMounts:
            - name: kickstart-%ID%
              mountPath: /kickstart/
      volumes:
        - name: kickstart-%ID%
          configMap:
            name: kickstart-%ID%
            defaultMode: 0777

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
apiVersion: v1
kind: Service
metadata:
  name: %ID%-pri
  namespace: dev
  labels:
    purpose: database
spec:
  selector:
    role: %ID%-pri
  ports:
    - name: mongo
      port: 27018
      targetPort: 27018
    - name: node-exporter
      port: 9091
      targetPort: 9091
  type: ClusterIP
---
apiVersion: v1
kind: Service
metadata:
  name: %ID%-sec-1
  namespace: dev
  labels:
    purpose: database
spec:
  selector:
    role: %ID%-sec-1
  ports:
    - name: mongo
      port: 27018
      targetPort: 27018
    - name: node-exporter
      port: 9091
      targetPort: 9091
  type: ClusterIP
---
apiVersion: v1
kind: Service
metadata:
  name: %ID%-sec-2
  namespace: dev
  labels:
    purpose: database
spec:
  selector:
    role: %ID%-sec-2
  ports:
    - name: mongo
      port: 27018
      targetPort: 27018
    - name: node-exporter
      port: 9091
      targetPort: 9091
  type: ClusterIP
---
kind: Pod
apiVersion: v1
metadata:
  name: %ID%-configurator-pod
  namespace: dev
spec:
  containers:
    - name: %ID%-configurator-container
      image: localhost:32000/mongo-cst:1.0
      imagePullPolicy: Always
      command: ['/bin/sh', '-c','/kickstart/configurator.sh']
      volumeMounts:
        - name: kickstart-%ID%
          mountPath: /kickstart/
        - name: kickstart-shard-%ID%
          mountPath: /kickstart-shard/
  volumes:
    - name: kickstart-%ID%
      configMap:
        name: kickstart-%ID%
        defaultMode: 0777
    - name: kickstart-shard-%ID%
      configMap:
        name: add-shard-%ID%
  restartPolicy: Never
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
