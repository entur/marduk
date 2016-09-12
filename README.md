# Marduk

For details, see the
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

## Liveness and readyiness
In production, nabu can be probed with:
- http://<host>:<port>/jersey/appstatus/up
- http://<host>:<port>/jersey/appstatus/ready
to check liveness and readiness, accordingly

* Marduk currently has two spring profiles, dev and test. Use the application.properties file to switch between these.
* The application is unable to run without configuration. This must be defined externally to the application in a file called application.properties. Copy application.properties into either the current directory, i.e. where the application will be run from, or a /config subdirectory of this folder
* Typical application.properties for dev environment, with file system blobstore:

```
spring.main.sources=no.rutebanken.marduk

# the options from org.apache.camel.spring.boot.CamelConfigurationProperties can be configured here
camel.springboot.name=Marduk

marduk.shutdown.timeout=1

# Nabu rest service
nabu.rest.service.url=http://nabu:8080/jersey/

# activemq settings
# TODO is spring.activemq.broker-url in use??
spring.activemq.broker-url=tcp://activemq:61616?jms.useAsyncSend=true&wireFormat.maxFrameSize=524288000&wireFormat.maxInactivityDuration=120000
activemq.broker.host=activemq
activemq.broker.mgmt.port=8161
spring.activemq.pooled=true
spring.activemq.user=admin
spring.activemq.password=your_password

# JPA settings
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=create

# chouette import/export settings
# NOTE! http4 is correct config!
chouette.url=http4://chouette:8080
# optional
chouette.max.retries=500
# optional
chouette.retry.delay=30000
# optional
chouette.export.days.forward=365
# optional
chouette.export.days.back=365

# sftp settings
sftp.host=lamassu:2224
# private key file
sftp.keyfile=/opt/jboss/.ssh/lamassu.pem

# otp graph building settings
otp.graph.build.directory=files/otpgraph
# optional (NOTE! http4 is correct config!)
otp.graph.deployment.notification.url=http4://<url>
otp.graph.blobstore.subdirectory=output/otpgraphs

# logging settings
logging.config=classpath:logback.xml
logging.level.no.rutebanken=DEBUG
logging.level.org.apache=INFO

spring.profiles.active=dev

```
* Typical application properties for test environment, with GCS blobstore:

```
spring.main.sources=no.rutebanken.marduk

# the options from org.apache.camel.spring.boot.CamelConfigurationProperties can be configured here
camel.springboot.name=Marduk

marduk.shutdown.timeout=300

# Nabu rest service
nabu.rest.service.url=http://nabu:8080/jersey/

# activemq settings
spring.activemq.broker-url=tcp://activemq:61616?jms.useAsyncSend=true&wireFormat.maxFrameSize=524288000&wireFormat.maxInactivityDuration=120000
spring.activemq.pooled=true
spring.activemq.user=admin
spring.activemq.password=admin

activemq.broker.name=amqp-srv1
activemq.broker.host=activemq
activemq.broker.mgmt.port=8161

# Google Cloud Storage blobstore settings
blobstore.containerName=marduk-test
blobstore.gcs.credential.path=/home/tomgag/Downloads/Carbon-a4d50ca8176c.json
blobstore.gcs.projectId=carbon-1287

# JPA settings
spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=create

# JPA settings (postgres)
# spring.jpa.database=POSTGRESQL
# spring.datasource.platform=postgres
# spring.jpa.show-sql=true
# spring.jpa.hibernate.ddl-auto=none
# spring.database.driverClassName=org.postgresql.Driver
# spring.datasource.url=jdbc:postgresql://localhost:5532/postgres

# chouette import/export settings
# NOTE! http4 is correct config!
chouette.url=http4://chouette:8080
# optional
chouette.max.retries=500
# optional
chouette.retry.delay=30000
# optional
chouette.export.days.forward=365
# optional
chouette.export.days.back=365

# sftp settings
sftp.host=lamassu:2224
# private key file
sftp.keyfile=/opt/jboss/.ssh/lamassu.pem

# otp graph building settings
otp.graph.build.directory=files/otpgraph
otp.graph.purge.queue=true
# optional (NOTE! http4 is correct config!)
otp.graph.deployment.notification.url=http4://<url>
otp.graph.map.base.url=http4://jump.rutebanken.org
otp.graph.blobstore.subdirectory=output/otpgraphs

# logging settings
logging.config=classpath:logback.xml
logging.level.no.rutebanken=INFO
logging.level.org.apache=INFO

spring.profiles.active=test

# Graph registration endpoint in etcd
etcd.graph.notification.url=http://etcd-client:2379/v2/keys/prod/otp/marduk.file
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
