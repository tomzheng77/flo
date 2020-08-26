#!/bin/sh

lein clean
lein cljsbuild once min
lein uberjar
cp target/flo.jar /flo/flo.jar
