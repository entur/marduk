FROM openjdk:11-jre
ADD target/marduk-*-SNAPSHOT.jar marduk.jar

EXPOSE 8080
CMD java $JAVA_OPTIONS -jar /marduk.jar