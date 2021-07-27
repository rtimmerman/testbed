FROM debian:buster

RUN apt-get update
RUN apt-get install -y gnupg wget curl tmux sudo vim
RUN wget -qO - https://www.mongodb.org/static/pgp/server-4.4.asc | apt-key add - 
RUN echo "deb http://repo.mongodb.org/apt/debian buster/mongodb-org/4.4 main" | tee /etc/apt/sources.list.d/mongodb-org-4.4.list

RUN apt-get update
RUN apt-cache search mongodb
RUN apt-get -y install mongodb-org-shell
RUN apt-get -y install openjdk-11-jdk-headless
#RUN apt-get install openjdk-jdk-11 -y

ENTRYPOINT /bin/sh -c ' while :; do sleep 1; done'
