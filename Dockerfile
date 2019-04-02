FROM openjdk:11
COPY target/scala-2.12/consumer-unarchive.jar /
ENTRYPOINT ["java","-jar","consumer-unarchive.jar"]