#!/bin/sh
/flo/datomic/bin/transactor dev-transactor-template.properties &
java -jar /flo/flo.jar
