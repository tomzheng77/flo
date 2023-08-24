#!/bin/sh

BASEDIR=$(dirname "$0")
docker login
lein clean
lein cljsbuild once min
lein uberjar
docker build -t tomzheng77/flo-server:1.0 $BASEDIR
docker push tomzheng77/flo-server:1.0

