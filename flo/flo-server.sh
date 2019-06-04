#!/bin/sh
iptables -A INPUT -p tcp --dport 3451 -s 127.0.0.1 -j ACCEPT
iptables -A INPUT -p tcp --dport 3451 -j DROP
datomic/bin/transactor dev-transactor-template.properties &
java -jar flo/flo.jar "ZtrgPz9sdYw9" 3451 flo-server
