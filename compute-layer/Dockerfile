FROM debian:bookworm

RUN useradd operations

SHELL ["/bin/bash", "-c"]

# RUN mkdir /root/mandelbrot-generator
#COPY mandelbrot-generator /root/mandelbrot-generator
COPY ./node_exporter-1.0.1.linux-amd64.tar.gz /root/
COPY ./upscript.sh /root/
COPY ./Submitter.class /root/
COPY ./Submitter.java /root/
RUN chmod 755 /root/upscript.sh

RUN apt-get update
RUN apt-get install -y openjdk-17-jdk-headless
RUN apt-get install -y curl zip unzip wget at vim procps
#RUN curl -s 'https://get.sdkman.io' | bash
#RUN source /root/.bashrc
#RUN source "/root/.sdkman/bin/sdkman-init.sh"
#RUN /bin/bash -c '. /root/.bashrc; sdk install sbt'

WORKDIR /root
RUN cd /root
RUN tar -zxf ./node_exporter-1.0.1.linux-amd64.tar.gz
RUN atd

ENTRYPOINT /bin/bash -c /root/upscript.sh 

