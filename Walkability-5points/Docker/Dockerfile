FROM openjdk:8-jre-alpine
FROM maven:3.3-jdk-8

WORKDIR /app

COPY . /app

RUN wget -O activemq.tar.gz http://archive.apache.org/dist/activemq/5.15.6/apache-activemq-5.15.6-bin.tar.gz
RUN tar -xzf activemq.tar.gz
RUN mvn clean
RUN mvn compile
