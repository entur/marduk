# Marduk

For details, see the 
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

* Build: `mvn clean install`
* Local run: `java -Xmx1280m -jar target/marduk-0.0.1-SNAPSHOT.jar`
* Docker image: `mvn -Pf8-build`
* Run the docker image in, eh, docker, choose one of:
     * `docker run -it --name marduk -e JAVA_OPTIONS="-Xmx1280m" --link activemq rutebanken/marduk:0.0.1-SNAPSHOT`
     * `mvn docker:start `
* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html