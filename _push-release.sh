#!/usr/bin/env bash

VERSION=$1
RELEASE_FOLDER="release-goby_${VERSION}"
tar zcvf release-goby_${VERSION}.tgz ${RELEASE_FOLDER}
#scp release-goby_${VERSION}.tgz www@okeeffe:/var/www/dirs/chagall/goby/releases/
#git tag "r${VERSION}"
#git push