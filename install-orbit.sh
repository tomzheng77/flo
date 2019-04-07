#!/bin/sh

lein uberjar
scp limiter.jar root@103.29.84.69:/root/limiter.jar
