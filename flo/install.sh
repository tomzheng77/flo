#!/bin/sh

lein cljsbuild once dev
lein uberjar
cp target/flo.jar /home/tomzheng/flo/flo.jar

