# Marduk

For details, see the
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

* Marduk currently has two spring profiles, dev and test. Use `-Dspring.profiles.active=dev` and `-Dspring.profiles.active=test` to switch between these.
* The application is unable to run without configuration. This must be defined externally to the application in a file called application.properties. Copy application.properties into either the current directory, i.e. where the application will be run from, or a /config subdirectory of this folder
* Typical application.properties for dev environment, with file system blobstore:

```
# activemq settings
spring.activemq.broker-url=tcp://activemq:61616?jms.useAsyncSend=true&wireFormat.maxFrameSize=524288000&wireFormat.maxInactivityDuration=120000
spring.activemq.pooled=true
spring.activemq.user=admin
spring.activemq.password=your_password

# File system blobstore settings
blobstore.provider=filesystem
blobstore.containerName=test-container
blobstore.filesystem.baseDirectory=./files/filesystemstorage

# chouette import/export settings
chouette.url=http4://chouette:8084

# sftp settings
sftp.host=lamassu:2224
# private key file
sftp.keyfile=/opt/jboss/.ssh/lamassu.pem

# otp graph building settings
jenkins.url=https4://jenkins.rutebanken.org/job/otpgraph

# logging settings
logging.level.no.rutebanken=DEBUG
logging.level.org.apache=INFO

```
* Typical application properties for test environment, with S3 blobstore:

```
# activemq settings
spring.activemq.broker-url=tcp://activemq:61616?jms.useAsyncSend=true&wireFormat.maxFrameSize=524288000&wireFormat.maxInactivityDuration=120000
spring.activemq.pooled=true
spring.activemq.user=admin
spring.activemq.password=admin

# Amazon S3 blobstore settings
blobstore.provider=aws-s3
blobstore.containerName=junit-test-rutebanken
blobstore.aws-s3.identity=AKIAIBRVMDRJKQBK6YEQ
blobstore.aws-s3.credential=2fRLhBLqhGcqT8mSr/450QVecdw84LQetF3T44uQ

# chouette settings
chouette.url=http4://chouette:8080

# sftp settings
sftp.host=lamassu:2224
# private key file
sftp.keyfile=/opt/jboss/.ssh/lamassu.pem

# otp graph building settings
jenkins.url=https4://jenkins.rutebanken.org/job/otpgraph

# logging settings
logging.level.no.rutebanken=INFO
logging.level.org.apache=INFO
logging.level.org.jclouds=INFO

```

* Run with maven `mvn spring-boot:run -Dspring.profiles.active=dev`

* Build: `mvn clean install`
* Local run: `java -Xmx1280m -Dspring.profiles.active=dev -jar target/marduk-0.0.1-SNAPSHOT.jar`
* Docker image: `mvn -Dspring.profiles.active=dev -Pf8-build`
* Run the docker image in docker inside vagrant:

     ```bash
     docker rm -f marduk ; mvn -Pf8-build && docker run -it --name marduk -e JAVA_OPTIONS="-Xmx1280m -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 -Dspring.profiles.active=dev" --link activemq --link lamassu --link chouette -p 5005:5005 -v ~/.ssh/lamassu.pem:/opt/jboss/.ssh/lamassu.pem:ro -v /git/marduk_config/dev/application.properties:/app/config/application.properties:ro dr.rutebanken.org/rutebanken/marduk:0.0.1-SNAPSHOT```

  Here, we mount the lamssu key and an application.properties file from vagrant.

* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html
