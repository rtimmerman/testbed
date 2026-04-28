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
# This file has been generated dynamically using generate-mongo-config-server-yml.pl, edit that file and re-run.
kind: Namespace
apiVersion: v1
metadata:
  name: dev
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: config-server-1
  namespace: dev
  labels:
    purpose: database
spec:
  replicas: 1
  selector:
    matchLabels:
      role:  config-server-1
      purpose: database
  template:
    metadata:
      labels:
        role: config-server-1
        purpose: database
    spec:
      containers:
        - name: config-server-1-pod
          image: localhost:32000/mongo-cst:1.0
          imagePullPolicy: Always
          command:
            - 'sh'
            - '-c'
            - '/kickstart/mongod-launcher.sh'
          volumeMounts:
            - name: kickstart-dir
              mountPath: /kickstart/
      volumes:
        - name: kickstart-dir
          configMap:
            name: rs-config
            defaultMode: 0777
      # nodeSelector:
      #   role: general-services
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
  name: config-server-svc-1
  namespace: dev
  labels:
    purpose: database
spec:
  selector:
      role: config-server-1
      purpose: database
  ports:
    - port: 27019
      targetPort: 27019
  type: ClusterIP
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: config-server-2
  namespace: dev
  labels:
    purpose: database
spec:
  replicas: 1
  selector:
    matchLabels:
      role:  config-server-2
  template:
    metadata:
      labels:
        role: config-server-2
    spec:
      containers:
        - name: config-server-2-pod
          image: localhost:32000/mongo-cst:1.0
          imagePullPolicy: Always
          command:
            - 'mongod'
            - '--configsvr'
            - '--replSet'
            - 'configServerRS'
            - '--tlsMode'
            - 'requireTLS'
            - '--clusterAuthMode'
            - 'x509'
            - '--tlsClusterFile'
            - '/kickstart/mongos-1-svc.pem'
            - '--tlsCertificateKeyFile'
            - '/kickstart/config-server-2.pem'
            - '--tlsCAFile'
            - '/kickstart/root-ca.pem'
            - '--tlsAllowInvalidCertificates'
            - '--bind_ip'
            - '*'
            # - '--dbpath'
          volumeMounts:
            - name: kickstart-dir
              mountPath: /kickstart/
      volumes:
        - name: kickstart-dir
          configMap:
            name: rs-config
            defaultMode: 0777
          # - '/var/lib/mongod'
          #      nodeSelector:
          #cloud.google.com/gke-nodepool: default-pool
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
      # nodeSelector:
      # role: general-services

---
kind: Service
apiVersion: v1
metadata:
  name: config-server-svc-2
  namespace: dev
  labels:
    purpose: database
spec:
  selector:
      role: config-server-2
  ports:
    - port: 27019
      targetPort: 27019
  type: ClusterIP
---
kind: Deployment
apiVersion: apps/v1
metadata:
  name: config-server-3
  namespace: dev
  labels:
    purpose: database
spec:
  replicas: 1
  selector:
    matchLabels:
      role:  config-server-3
  template:
    metadata:
      labels:
        role: config-server-3
    spec:
      containers:
        - name: config-server-3-pod
          image: localhost:32000/mongo-cst:1.0
          imagePullPolicy: Always
          command:
            #- 'sh'
            #- '-c'
            #- '/kickstart/mongod-launcher.sh'
            - 'mongod'
            - '--configsvr'
            - '--replSet'
            - 'configServerRS'
            - '--tlsMode'
            - 'requireTLS'
            - '--clusterAuthMode'
            - 'x509'
            - '--tlsClusterFile'
            - '/kickstart/mongos-1-svc.pem'
            - '--tlsCertificateKeyFile'
            - '/kickstart/config-server-3.pem'
            - '--tlsCAFile'
            - '/kickstart/root-ca.pem'
            - '--sslAllowInvalidCertificates'
            - '--bind_ip'
            - '*'
            #- '--dbpath'
            #- '/var/lib/mongod'
          volumeMounts:
            - name: kickstart-dir
              mountPath: /kickstart/
      volumes:
        - name: kickstart-dir
          configMap:
            name: rs-config
            defaultMode: 0777
            #     nodeSelector:
            #cloud.google.com/gke-nodepool: default-pool
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
  name: config-server-svc-3
  namespace: dev
  labels:
    purpose: database
spec:
  selector:
      role: config-server-3
  ports:
    - port: 27019
      targetPort: 27019
  type: ClusterIP
---
kind: PersistentVolume
apiVersion: v1
metadata:
  name: kickstart-dir
  namespace: dev
spec:
  capacity:
    storage: 10Gi
  accessModes:
    - ReadWriteMany
  gcePersistentDisk:
    pdName: 'kickstart-disk'
    fsType: ext4
---
kind: PersistentVolumeClaim
apiVersion: v1
metadata:
  name: kickstart-dir-claim
  namespace: dev
spec:
  accessModes:
    - ReadWriteMany
  storageClassName: ''
  resources:
    requests:
        storage: 10Gi
--- 
kind: ConfigMap
apiVersion: v1
metadata:
  name: rs-config
  namespace: dev
data:
  mongod-launcher.sh: |
    mongod --bind_ip_all &
    #mongo --norc --nodb --tls --tlsCertificateKeyFile /kickstart/config-server.pem --tlsCAFile /kickstart/root-ca.pem /kickstart/add-user.js
    mongosh --norc /kickstart/add-user.js
    kill -9 $(ps aux | grep mongod | grep bind_ip_all | awk '{print $2}')
    mongod --configsvr \
    --replSet configServerRS \
    --tlsMode requireTLS \
    --clusterAuthMode x509 \
    --tlsClusterFile /kickstart/mongos-1-svc.pem \
    --tlsCertificateKeyFile /kickstart/config-server.pem \
    --tlsCAFile /kickstart/root-ca.pem \
    --tlsAllowInvalidCertificates \
    --bind_ip \*
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
    outcome = db.runCommand(
      {
        createUser: "OU=SCMS,O=Birkbeck,emailAddress=rtimme01@bbk.ac.uk,C=GB,ST=Essex,CN=config-server-svc-1",
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
    );
    printjson(outcome);

    outcome = db.runCommand(
      {
        createUser: "OU=SCMS,O=Birkbeck,emailAddress=rtimme01@bbk.ac.uk,C=GB,ST=Essex,CN=mongos-1-svc",
        roles: [
          {role: 'readWrite', db: 'config'},
        ],
        writeConcern: {
          w: 'majority',
          wtimeout: 5000
        }
      }
    );

    printjson(outcome);
  shard-configdb.js: |
    result = rs.initiate({
      _id: "configServerRS",
      configsvr: true,
      members: [
          { _id: 0, host: "config-server-svc-1:27019", priority: 2 },
          { _id: 1, host: "config-server-svc-2:27019", priority: 1 },
          { _id: 2, host: "config-server-svc-3:27019", priority: 1 }
      ]
    });

    printjson(result);
  waitscript.pl: |
    use Socket;

    $| = 1;

    socket(SOCKET1, PF_INET, SOCK_STREAM, (getprotobyname('tcp'))[2]);
    socket(SOCKET2, PF_INET, SOCK_STREAM, (getprotobyname('tcp'))[2]);
    socket(SOCKET3, PF_INET, SOCK_STREAM, (getprotobyname('tcp'))[2]);

    $waiting = 1;
    while ($waiting) {
      sleep 1;
      print "Attempting to connect...\n";
      
      (
        connect(SOCKET1, pack_sockaddr_in(27019, inet_aton("config-server-svc-1")))
        and connect(SOCKET2, pack_sockaddr_in(27019, inet_aton("config-server-svc-2")))
        and connect(SOCKET3, pack_sockaddr_in(27019, inet_aton("config-server-svc-3")))
      ) or next;

      $waiting = 0;
    }

    close(SOCKET1);
    close(SOCKET2);
    close(SOCKET3);

    printf "Connected!\n";

    print `mongosh --tls --tlsAllowInvalidCertificates --tlsCertificateKeyFile /kickstart/config-server.pem --tlsCAFile /kickstart/root-ca.pem --host config-server-svc-1 --authenticationDatabase '$external' --authenticationMechanism MONGODB-X509 --port 27019 /kickstart/shard-configdb.js `;

    exit(0);
  root-ca.pem: |
    {!import security/cacert.pem}
  config-server.pem: |
    {!import security/certs/config-server-svc-1.pem}
    {!decrypt security/config-server-svc-1-key.pem}
  config-server-2.pem: |
    {!import security/certs/config-server-svc-2.pem}
    {!decrypt security/config-server-svc-2-key.pem}
  config-server-3.pem: |
    {!import security/certs/config-server-svc-3.pem}
    {!decrypt security/config-server-svc-3-key.pem}
  mongos-1-svc.pem: |
    {!import security/certs/mongos-1-svc.pem}
    {!decrypt security/mongos-1-svc-key.pem}
---
# This file has been generated dynamically using generate-mongo-config-server-yml.pl, edit that file and re-run.
kind: Pod
apiVersion: v1
metadata:
  name: configurator-pod
  namespace: dev
spec:
  containers:
    - name: configurator-container
      image: mongo
      command: ['/usr/bin/perl', '/kickstart/waitscript.pl']
      volumeMounts:
        - name: kickstart-dir
          mountPath: /kickstart/
  volumes:
    - name: kickstart-dir
      configMap:
        name: rs-config
    # - name: scripts-dir
    #   persistentVolumeClaim:
    #     claimName: scripts-dir-claim
  restartPolicy: Never
  # nodeSelector:
  # role: general-services
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
