FROM openjdk:17
COPY target/flo.jar /flo/flo.jar
WORKDIR /rundir
EXPOSE 5432
CMD ["java", "-jar", "/flo/flo.jar", "--db", "default"]

