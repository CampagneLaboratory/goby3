#!/usr/bin/env bash

VERSION=$1
RELEASE_FOLDER="release-goby_${VERSION}"
if [ -d "$RELEASE_FOLDER" ]; then
    tar zcvf release-goby_${VERSION}.tgz ${RELEASE_FOLDER}
    #scp release-goby_${VERSION}.tgz www@okeeffe:/var/www/dirs/chagall/goby/releases/
    #git tag "r${VERSION}"
    #git push
else
    echo "$RELEASE_FOLDER does not exist. Did you prepare a release to push for $VERSION?"
fi
