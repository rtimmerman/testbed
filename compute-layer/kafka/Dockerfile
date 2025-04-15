FROM alpine:3.21.0

RUN apk add --no-cache openjdk17-jre-headless bash
RUN apk add tree

SHELL ["/bin/bash", "-c"]

COPY ./kafka /var/kafka

COPY ./server-boot.sh /var/kafka/server-boot.sh
RUN chmod 0755 /var/kafka/server-boot.sh

WORKDIR /var/kafka

ENTRYPOINT /var/kafka/server-boot.sh

