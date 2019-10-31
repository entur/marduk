FROM openjdk:11-jre
WORKDIR /deployments
ADD target/marduk-*-SNAPSHOT.jar marduk.jar
RUN mkdir /root/.ssh \
 && touch /root/.ssh/known_hosts
CMD java $JAVA_OPTIONS -jar marduk.jar
