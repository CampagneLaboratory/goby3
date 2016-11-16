#!/usr/bin/env bash

DL_VERSION=$1
WORKDIR=`pwd`
cd ..
mvn -pl :goby-framework antrun:run@prepare-goby-release -Ddl.version=${DL_VERSION}
cd ${WORKDIR}
