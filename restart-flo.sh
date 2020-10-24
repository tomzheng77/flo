#!/bin/sh

tmux kill-session -t flo
tmux new-session -d -s "flo" "java -jar /flo/flo.jar --db flo-ace"
