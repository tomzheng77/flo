#!/bin/sh

lein clean
lein cljsbuild once min
lein uberjar
cp flo-server.sh /root/flo-server.sh
cp flo-public.sh /root/flo-public.sh
cp target/flo.jar /root/flo/flo.jar
