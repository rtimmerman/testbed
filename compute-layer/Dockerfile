FROM debian:bullseye

RUN useradd operations

SHELL ["/bin/bash", "-c"]

RUN mkdir /root/mandelbrot-generator

COPY mandelbrot-generator /root/mandelbrot-generator

RUN apt update
RUN apt install -y openjdk-11-jdk-headless
RUN apt install -y curl zip unzip wget
RUN curl -s 'https://get.sdkman.io' | bash
RUN source /root/.bashrc
RUN source "/root/.sdkman/bin/sdkman-init.sh"
RUN /bin/bash -c '. /root/.bashrc; sdk install sbt'
#RUN sdk install scala 2.13.6

ENTRYPOINT /bin/sh -c 'while :; do sleep 1; done'

