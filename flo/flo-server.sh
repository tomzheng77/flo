#!/bin/sh
datomic/bin/transactor dev-transactor-template.properties &
java -jar flo/flo.jar "ZtrgPz9sdYw9" 3451 flo-server
