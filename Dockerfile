FROM adoptopenjdk/openjdk11:alpine-jre
WORKDIR /deployments
COPY target/marduk-*-SNAPSHOT.jar marduk.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
RUN mkdir -p /home/appuser/.ssh \
 && touch /home/appuser/.ssh/known_hosts
CMD java $JAVA_OPTIONS -jar marduk.jar
