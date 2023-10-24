#!/bin/bash
source /root/.bashrc

cd /root/mandelbrot-generator
if [ ${ROLE} = "consumer" ] || [ ${ROLE} = "data-writer-consumer" ] || [ ${ROLE} = "producer" ]; then 
    echo "** launching a ${ROLE} node **"
    #setsid sbt "run ${ROLE} ${TOPIC}" &
    setsid java -cp ./target/scala-3.3.0/mandelbrot-generator-assembly-0.1.0-SNAPSHOT.jar example.mandelbrot ${ROLE} ${TOPIC}

    # entry point
    /root/node_exporter-1.0.1.linux-amd64/node_exporter --web.listen-address=0.0.0.0:9091
fi

if [ ${ROLE} = "producer-shell" ]; then
    echo '** Producer Shell Ready **'
    cd /root

    while [ 1 ]; do
        sleep 1
    done
fi

