#!/usr/bin/env bash

VERSION=$1
RELEASE_FOLDER="release-goby_${VERSION}"
if [ -d "$RELEASE_FOLDER" ]; then
    # remove any previous archive for this release
    rm -f release-goby_${VERSION}.tgz
    tar zcvf release-goby_${VERSION}.tgz ${RELEASE_FOLDER}
    scp release-goby_${VERSION}.tgz www@okeeffe:/var/www/dirs/chagall/goby/releases/
    ssh www@okeeffe << END
        cd /var/www/dirs/chagall/goby/releases/
        tar zxvf release-goby_${VERSION}.tgz
        chmod -R a+r release-goby_${VERSION}
        rm -f latest-release
        ln -s release-goby_${VERSION} latest-release
        cd archive
        ln -s ../release-goby_${VERSION} .
END
    git tag "r${VERSION}"
    #git push --tags
else
    echo "$RELEASE_FOLDER does not exist. Please, run prepare-release.sh to prepare the release files for $VERSION and then try again."
fi
