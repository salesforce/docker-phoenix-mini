# docker-phoenix-mini

A mini [Apache Phoenix](https://phoenix.apache.org/) cluster for local development and testing based on the official [HBaseTestingUtility](https://github.com/apache/hbase/blob/master/hbase-server/src/test/java/org/apache/hadoop/hbase/HBaseTestingUtility.java).

The key configurations to make the embedded server working in docker are
the following:

|   |  Default  |Override|
|---| ----------|-------|
|hbase.zookeeper.quorum|localhost|HBASE_ZOOKEEPER_QUORUM|
|test.hbase.zookeeper.property.clientPort|2181|N/A|
|hbase.localcluster.assign.random.ports|false|N/A|
|hbase.master.hostname|localhost|HBASE_MASTER_HOSTNAME|
|hbase.master.ipc.address|0.0.0.0|N/A|
|hbase.regionserver.hostname|localhost|HBASE_REGIONSERVER_HOSTNAME|
|hbase.regionserver.ipc.address|0.0.0.0|N/A|

## Build

Generate the executable jar `docker-phoenix-mini.jar` and the docker image.

> mvn package

## Docker

All `sql` scripts copied under the directory `/opt/hbase/schema` will be executed.
The container will report the status as `healthy` as soon as the server is up and
running and all `sql` have been executed.

An example of how to run the container via docker-compose:

```yaml
hbase:
    image: docker-phoenix-mini
    container_name: docker-phoenix-mini
    ports:
      - 2181:2181
      - 16000:16000
      - 16010:16010
      - 16020:16020
      - 16030:16030
      - 8080:8080
      - 8085:8085
    volumes:
      - ./hbase/schema:/opt/hbase/schema
```

## Health check

`docker-phoenix-mini` has a built-in health check endpoint `http://localhost:18080/health` that reports `200` 
when the cluster is UP and running **and** the schema migrations executed.

The container ships with a built-in *HEALTHCHECK* that reports the health status back to docker: 

> docker inspect -f '{{.State.Health}}' docker-phoenix-mini

or 

> docker inspect --format='{{json .State.Health}}' docker-phoenix-mini | jq -r .Status

When the container is `healthy` it should output something like :

```
{healthy 0 [0xc0002980a0 0xc0002980f0 0xc000298140 0xc000298190 0xc0002981e0]}
```

## Ports

|Key|Port|Url|
|---|---|---|
|health check|18080| http://localhost:18080/health|
|hbase.zookeeper.property.clientPort|2181| |
|hbase.master.port|16000
|hbase.master.info.port|16010|http://localhost:16010/master-status|
|hbase.regionserver.port|16020||
|hbase.regionserver.info.port|16030|http://localhost:16030/rs-status|
|hbase.rest.port|8080||
|hbase.rest.info.port|8085||