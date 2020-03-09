#!/bin/sh

lein clean
lein cljsbuild once min
lein uberjar
