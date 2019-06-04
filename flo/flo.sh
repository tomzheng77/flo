#!/bin/sh
datomic/bin/transactor dev-transactor-template.properties &
java -jar flo/flo.jar "" 3451 flo-ace
