#!/bin/sh

export PATH="/usr/lib/jvm/java-1.8.0-openjdk-amd64/bin/:$PATH"
lein clean
lein cljsbuild once min
lein uberjar
cp target/flo.jar /flo/flo.jar
