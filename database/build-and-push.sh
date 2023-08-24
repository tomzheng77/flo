#!/bin/sh
BASEDIR=$(dirname "$0")
docker build -t tomzheng77/flo-database:1.0 $BASEDIR
docker push tomzheng77/flo-database:1.0

