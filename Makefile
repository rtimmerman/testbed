setup-cluster:
	gcloud beta container --project "wired-depot-274918" clusters create "cluster-1" --zone "europe-west6-b" --no-enable-basic-auth --cluster-version "1.16.15-gke.6000" --machine-type "e2-medium" --image-type "COS" --disk-type "pd-standard" --disk-size "100" --metadata disable-legacy-endpoints=true --scopes "https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" --num-nodes "3" --enable-stackdriver-kubernetes --enable-ip-alias --network "projects/wired-depot-274918/global/networks/default" --subnetwork "projects/wired-depot-274918/regions/europe-west6/subnetworks/default" --default-max-pods-per-node "110" --no-enable-master-authorized-networks --addons HorizontalPodAutoscaling,HttpLoadBalancing --enable-autoupgrade --enable-autorepair --max-surge-upgrade 1 --max-unavailable-upgrade 0

setup-cluster-get-credentials:
	gcloud container clusters get-credentials --zone="europe-west6-b" cluster-1

create-5-node-mongo:
	# config server
	kubectl apply -f mongo-config-svc.yml
	sleep 1

	# mongos
	kubectl apply -f mongo-mongos.yml

	# filestore
	kubectl apply -f filestore.yml
	
	sleep 30

	# shards
	for i in {1..5} ; do \
		kubectl apply -f mongo-shard$$i.yml ; \
	done

	# monitor
	kubectl apply -f monitor.yml

update-mongo-host:
	sudo perl -pi.bak -e "s/^.*mongos-1-svc/`kubectl get services --output=wide --namespace=dev mongos-1-svc | awk 'NR>1{print $$4}'` mongos-1-svc/" /etc/hosts
delete-5-node-mongo:
	# monitor
	kubectl delete --ignore-not-found --grace-period=1 -f monitor.yml

	# shards
	for i in {1..5} ; do \
		kubectl delete --ignore-not-found --grace-period=1 -f mongo-shard$$i.yml ; \
	done

	# filestore
	kubectl delete --ignore-not-found --grace-period=1 -f filestore.yml

	# mongos
	kubectl delete --ignore-not-found --grace-period=1 -f mongo-mongos.yml

	# config server
	kubectl delete --ignore-not-found --grace-period=1 -f mongo-config-svc.yml

delete-cluster:
	gcloud container clusters delete cluster-1 --zone europe-west6-b

wipe-ycsb-db:
	mongo --tls --tlsCAFile ./root-ca.pem --tlsCertificateKeyFile ./mongos-1-svc.pem --authenticationDatabase '$$external' --authenticationMechanism='MONGODB-X509' mongos-1-svc:27017 --eval 'db.getSiblingDB("ycsb").dropDatabase();'

login-db:
	mongo --tls --tlsCAFile ./root-ca.pem --tlsCertificateKeyFile ./mongos-1-svc.pem --authenticationDatabase '$$external' --authenticationMechanism='MONGODB-X509' mongos-1-svc:27017

setup-update-shard-configs:
	cat mongo-shard1.yml.template | perl shard-generator.pl arnold > mongo-shard1.yml
	cat mongo-shard1.yml.template | perl shard-generator.pl bairstow > mongo-shard2.yml
	cat mongo-shard1.yml.template | perl shard-generator.pl calvin > mongo-shard3.yml
	cat mongo-shard1.yml.template | perl shard-generator.pl duncan > mongo-shard4.yml
	cat mongo-shard1.yml.template | perl shard-generator.pl elliot > mongo-shard5.yml

