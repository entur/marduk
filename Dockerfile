FROM openjdk:11-jre
ADD target/marduk-0.0.1-SNAPSHOT.jar marduk.jar

EXPOSE 8080 8776
CMD ["java","-jar", "/marduk.jar", "$JAVA_OPTIONS"]
