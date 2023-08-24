FROM openjdk:11
WORKDIR /
COPY datomic-pro-1.0.6735 ./
EXPOSE 4334
CMD ["datomic-pro-1.0.6735/bin/transactor", "-Xmx8G", "dev-transactor-template.properties"]

