#!/bin/sh

tmux kill-session -t datomic
tmux kill-session -t flo
tmux new-session -d -s "datomic" "/flo/datomic-pro-0.9.5786/bin/transactor -Xmx8G dev-transactor-template.properties"
tmux new-session -d -s "flo" "java -jar /flo/flo.jar --db flo-ace"

