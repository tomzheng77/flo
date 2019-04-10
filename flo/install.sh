#!/bin/sh

lein cljsbuild once min
lein uberjar
cp target/flo.jar /home/tomzheng/flo/flo.jar

