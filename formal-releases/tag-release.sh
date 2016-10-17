#!/usr/bin/env bash

read -p "Are you sure to tag this release? [y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]] ; then
    WORKDIR=`pwd`
    cd ..
    mvn -pl :goby-framework antrun:run@tag-goby-release
    cd ${WORKDIR}
fi