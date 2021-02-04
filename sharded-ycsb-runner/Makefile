setup-append-mongo-host:
	docker-compose exec -u0 experiment /bin/bash -c "echo $(kubectl get services --output=wide --namespace=dev mongos-1-svc | awk 'NR>1{print $4}') mongos-1-svc >> /etc/hosts"
