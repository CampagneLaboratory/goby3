#!/usr/bin/env bash

DL_VERSION=$1
if [ -z "$DL_VERSION" ]; then
    echo "ERROR: The 'DL_VERSION' parameter is missing a value."
    echo "Usage: ./prepare-release DL_VERSION"
    exit 1
fi
WORKDIR=`pwd`
cd ..
mvn -pl :goby-framework antrun:run@prepare-goby-release -Ddl.version=$DL_VERSION
cd ${WORKDIR}




