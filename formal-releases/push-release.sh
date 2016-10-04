#!/usr/bin/env bash

read -p "Are you sure to push this release? [y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]
then
    WORKDIR=`pwd`
    cd ..
    mvn -pl :goby-framework antrun:run@push-goby-release
    cd ${WORKDIR}
fi

