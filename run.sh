#!/bin/zsh

set -e

export SCRIPT_DIR=${0:a:h}

mvn package  -DskipTests && java -Xmx40G -jar target/*with-deps.jar --force --download --building-merge-z13=false --area=planet --maxzoom=14 --threads=16
