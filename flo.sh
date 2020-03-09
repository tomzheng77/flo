#!/bin/sh
/flo/datomic/bin/transactor dev-transactor-template.properties &
java -jar /flo/flo.jar --port 3451 flo-ace
