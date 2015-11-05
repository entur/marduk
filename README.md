# Marduk

For details, see the 
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

* Build: `mvn clean install`
* Local run: `mvn spring-boot:run`
* Docker image: `mvn -Pf8-build`
* Run the docker image in, eh, docker, choose one of:
     * `mvn docker:start `
     * `docker run -it rutebanken/marduk:0.0.1-SNAPSHOT`
* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html