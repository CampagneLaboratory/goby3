#!/usr/bin/env bash

VERSION=$1

if [[ $VERSION == *"SNAPSHOT"* ]] ;then
  echo "Current version is set to ${VERSION}. Can't tag a snapshot!";
  exit 1
fi

WORKDIR=`pwd`
cd ..
git tag "r${VERSION}"
git push --tags
cd ${WORKDIR}