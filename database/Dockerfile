FROM openjdk:11
# FROM clojure
COPY datomic-pro-1.0.6735 /datomic
WORKDIR /datomic
EXPOSE 4334
CMD ["bin/transactor", "-Xmx8G", "dev-transactor-template.properties"]

