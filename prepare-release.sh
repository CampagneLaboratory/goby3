#!/usr/bin/env bash

mvn clean package
mkdir -p release-files
mv
mvn -pl :goby-framework assembly:single@make-goby-models
mv target/*.zip  release-files