#!/bin/sh

tmux kill-session -t flo
tmux new-session -d -s "flo" "java8 -jar /flo/flo.jar --db flo-ace"
