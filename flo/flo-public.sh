#!/bin/sh
iptables -A INPUT -p tcp --dport 3452 -s 127.0.0.1 -j ACCEPT
iptables -A INPUT -p tcp --dport 3452 -j DROP
datomic/bin/transactor dev-transactor-template.properties &
java -jar flo/flo.jar "ePq6pDeXzjFv" 3452 flo-public
