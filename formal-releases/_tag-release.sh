#!/usr/bin/env bash

VERSION=$1

read -p "Are you sure to tag this release as r${VERSION}? [y/N] " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]
then
    WORKDIR=`pwd`
    cd ..
    git tag "r${VERSION}"
    git push --tags
    cd ${WORKDIR}
fi
