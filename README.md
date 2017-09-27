# Marduk

For details, see the
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

## Liveness and readyiness
In production, marduk can be probed with:
- http://<host>:<port>/health/live
- http://<host>:<port>/health/ready
to check liveness and readiness, accordingly

* Marduk currently has two spring profiles, dev and test. Use the application.properties file to switch between these.
* The application is unable to run without configuration. This must be defined externally to the application in a file called application.properties. Copy application.properties into either the current directory, i.e. where the application will be run from, or a /config subdirectory of this folder
* Typical application.properties for dev environment:

```
marduk.shutdown.timeout=1

blobstore.gcs.container.name=marduk-test
blobstore.gcs.credential.path=/home/tomgag/.ssh/Carbon-ef49cabc6d04.json
blobstore.gcs.exchange.container.name=marduk-test-exchange
blobstore.gcs.exchange.credential.path=/home/tomgag/.ssh/Carbon-ef49cabc6d04.json
blobstore.delete.external.blobs=false
blobstore.gcs.project.id=carbon-1287

camel.springboot.name=Marduk
chouette.export.days.back=365
chouette.export.days.forward=365
chouette.max.retries=3000
chouette.retry.delay=5000
chouette.url=http4://localhost:8080
chouette.stats.validity.categories=10,17
chouette.stats.days=180

logging.config=classpath:logback.xml
logging.level.no=DEBUG
logging.level.no.rutebanken.marduk=INFO
logging.level.org=INFO
logging.level.org.opentripplanner=INFO
logging.level.org.apache.camel.util=INFO


logging.level.org.apache=INFO
logging.level.org.apache=INFO
logging.level.org.apache.http.wire=INFO
logging.level.org.apache.activemq=INFO
logging.level.org.apache.activemq.thread=INFO
logging.level.org.apache.activemq.transport=INFO
logging.level.org.apache.camel.component.file=INFO
logging.level.org.apache.camel.component.file.remote=INFO
logging.level.org.springframework=INFO
logging.level.no.rutebanken.marduk.routes.otp.GraphPublishRouteBuilder=INFO
logging.level.no.rutebanken.marduk.routes.file=DEBUG
providers.api.url=http://baba:11601/services/providers/
organisations.api.url=http://baba:11601/services/organisations/

nri.ftp.delay=40s
nri.ftp.folder=rutedata
nri.ftp.host=127.0.0.1
nri.ftp.password=anonymous
nri.ftp.username=anonymous
nri.ftp.autoStartup=false

otp.graph.blobstore.subdirectory=graphs
otp.graph.build.directory=files/otpgraph
fetch.osm.cron.schedule=0+0+*+*+*+?

server.admin.host=0.0.0.0
server.admin.port=8888
server.host=0.0.0.0
server.port=8776

sftp.host=192.168.99.100:30022
sftp.keyfile=/home/tomgag/.ssh/lamassu.pem
sftp.known.hosts.file=/home/tomgag/.ssh/known_hosts
sftp.autoStartup=false
sftp.delay=30s

spring.activemq.broker-url=tcp://localhost:51616?jms.redeliveryPolicy.maximumRedeliveries=0
activemq.broker.host=localhost
activemq.broker.mgmt.port=18161
spring.activemq.password=admin
spring.activemq.user=admin
spring.activemq.pooled=true
spring.main.sources=no.rutebanken.marduk
spring.profiles.active=gcs-blobstore
etcd.graph.notification.url=none
otp.graph.deployment.notification.url=none

idempotent.skip=false

tiamat.url=http4://tiamat:1888
babylon.url=http4://babylon:9030/rest

kartverket.username=
kartverket.password=

```
* Run with maven `mvn spring-boot:run -Dspring.profiles.active=dev`

* Build: `mvn clean install`
* Local run: `java -Xmx1280m -Dspring.profiles.active=dev -jar target/marduk-0.0.1-SNAPSHOT.jar`
* Docker image: `mvn -Dspring.profiles.active=dev -Pf8-build`
* Run the docker image in docker inside vagrant:

     ```docker rm -f marduk ; mvn -Pf8-build && docker run -it --name marduk -e JAVA_OPTIONS="-Xmx1280m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005" --link activemq --link lamassu --link chouette -p 5005:5005 -v ~/.ssh/lamassu.pem:/opt/jboss/.ssh/lamassu.pem:ro -v /git/config/marduk/dev/application.properties:/app/config/application.properties:ro dr.rutebanken.org/rutebanken/marduk:0.0.1-SNAPSHOT```

  Here, we mount the lamssu key and an application.properties file from vagrant.

* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html

## Using the admin services

You find REST services in the `.../marduk/rest/` package. These can be invoked by calling
them. The call is easiest performed within the pod. First you need to find
the pod ID, and then you can call the rest service as follows. The OSM map
fetch is used as an example:

```
kc exec -it marduk-dnt0p curl -- -v -XPOST http://0.0.0.0:8080/admin/services/fetch/osm
```
