#!/usr/bin/env bash

WORKDIR=`pwd`
cd ..
mvn -pl :goby-framework antrun:run@tag-goby-release
cd ${WORKDIR}

