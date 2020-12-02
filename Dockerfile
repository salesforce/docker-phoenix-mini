FROM openjdk:8-jre-slim

RUN apt-get update
RUN apt-get install -y curl

RUN groupadd --system --gid 7447 hbase
RUN adduser --system --gid 7447 --uid 7447 --shell /bin/bash --home /opt/hbase hbase

WORKDIR /opt/hbase
RUN mkdir schema

COPY target/libs /opt/hbase/libs

COPY target/docker-phoenix-mini.jar /opt/hbase/

RUN chown -R hbase:hbase .

USER hbase
ENV HOME=/opt/hbase

# health check (http://localhost:18080/health)
EXPOSE 18080

# hbase.zookeeper.property.clientPort
EXPOSE 2181

# hbase.master.port
EXPOSE 16000

# hbase.master.info.port (http://localhost:16010/master-status)
EXPOSE 16010

# hbase.regionserver.port
EXPOSE 16020

# hbase.regionserver.info.port (http://localhost:16030/rs-status)
EXPOSE 16030

# hbase.rest.port
EXPOSE 8080

# hbase.rest.info.port
EXPOSE 8085

HEALTHCHECK --interval=5s --timeout=1m CMD curl --fail -s http://localhost:18080/health || exit 1

CMD ${JAVA_HOME}/bin/java ${JVM_ARGS} -jar docker-phoenix-mini.jar
