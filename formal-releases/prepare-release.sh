#!/usr/bin/env bash

WORKDIR=`pwd`
cd ..
mvn -pl :goby-framework antrun:run@prepare-goby-release
cd ${WORKDIR}
