#!/bin/sh
iptables -A INPUT -p tcp --destination-port 3452 -j DROP
datomic/bin/transactor dev-transactor-template.properties &
java -jar flo/flo.jar "ePq6pDeXzjFv" 3452 flo-public
