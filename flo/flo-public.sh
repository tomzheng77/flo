#!/bin/sh
datomic/bin/transactor dev-transactor-template.properties &
java -jar flo/flo.jar "ePq6pDeXzjFv" 3452 flo-public
