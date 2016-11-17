#!/usr/bin/env bash +x

if [ $# -ne 2 ]; then
    echo "usage: prepare-previews GOBY_VERSION-SNAPSHOT DL_VERSION-SNAPSHOT"
    exit 1
fi

VERSION=$1
DL_VERSION=$2
if [[ ! $VERSION == *"SNAPSHOT"* ]] ;then
  echo "Current version is set to ${VERSION}, but you should only preview a snapshot (version must end in -SNAPSHOT)!";
  exit 1
fi
if [[ ! $DL_VERSION == *"SNAPSHOT"* ]] ;then
  echo "Current version is set to ${DL_VERSION}, but you should only preview a snapshot (version must end in -SNAPSHOT)!";
  exit 1
fi

rm -fr package
cd ..
mvn clean install
mkdir -p snapshot-previews/package
mvn -f dl-downloader.xml dependency:copy -Ddl.version=${DL_VERSION} -U
cp goby.jar somatic.jar framework.jar snapshot-previews/package/
cp goby snapshot-previews/package/
cp -r config snapshot-previews/package/
cp -r models snapshot-previews/package/
cd snapshot-previews/
rm goby-${VERSION}.zip
rm -fr goby-${VERSION}
mv package goby-${VERSION}
ls -ltrh goby-${VERSION}
zip -r goby-${VERSION}.zip goby-${VERSION}
