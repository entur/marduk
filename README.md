# Marduk

For details, see the 
initial project setup location:
  https://github.com/fabric8io/ipaas-quickstarts/

* Build: `mvn clean install`
* Local run: `java -Xmx1280m -jar target/marduk-0.0.1-SNAPSHOT.jar`
* Docker image: `mvn -Pf8-build`
* Run the docker image in docker on dev machine (you'll need to modify ports from 22 to 2224 and also to have the lamassu.pem file present in ~/.ssh):
     * `docker run -it --name marduk -e JAVA_OPTIONS="-Xmx1280m -Dspring.profiles.active=dev" --link activemq --add-host=lamassu:127.0.0.1 -v ~/.ssh/lamassu.pem:/opt/jboss/.ssh/lamassu.pem:ro rutebanken/marduk:0.0.1-SNAPSHOT` 
* Run the docker image in docker inside vagrant:
     * `docker run -it --name marduk -e JAVA_OPTIONS="-Xmx1280m -Dspring.profiles.active=dev" --link activemq --link lamassu -v ~/.ssh/lamassu.pem:/opt/jboss/.ssh/lamassu.pem:ro rutebanken/marduk:0.0.1-SNAPSHOT`
* For more docker plugin goals, see: http://ro14nd.de/docker-maven-plugin/goals.html