FROM openjdk:11
COPY target/flo.jar /flo/flo.jar
WORKDIR /flo
EXPOSE 5432
CMD ["java", "-jar", "flo.jar"]

