#!/bin/zsh

set -e

export SCRIPT_DIR=${0:a:h}

mvn package && java -jar target/*with-deps.jar --force --download --building-merge-z13=false --area=planet --threads=16
