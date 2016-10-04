#!/usr/bin/env bash

VERSION="3.0.2"
RELEASE_FOLDER="release-goby_${VERSION}"
tar zcvf release-goby_${VERSION}.tgz ${RELEASE_FOLDER}
scp ${RELEASE_FOLDER} www@okeeffe:/var/www/dirs/chagall/goby/releases/

git tag "r${VERSION}"
git push