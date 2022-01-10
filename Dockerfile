FROM adoptopenjdk/openjdk11:alpine-jre
RUN apk add --no-cache tini
WORKDIR /deployments
COPY target/marduk-*-SNAPSHOT.jar marduk.jar
RUN addgroup appuser && adduser --disabled-password appuser --ingroup appuser
USER appuser
RUN mkdir -p /home/appuser/.ssh \
 && touch /home/appuser/.ssh/known_hosts
CMD ["java", "-jar", "marduk.jar"]
