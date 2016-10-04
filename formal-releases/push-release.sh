#!/usr/bin/env bash

WORKDIR=`pwd`
cd ..
mvn -pl :goby-framework antrun:run@push-goby-release
cd ${WORKDIR}
