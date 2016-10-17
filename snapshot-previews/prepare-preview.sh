#!/usr/bin/env bash -x

VERSION=$1
if [[ ! $VERSION == *"SNAPSHOT"* ]] ;then
  echo "Current version is set to ${VERSION}, but you should only preview a snapshot (version must end in -SNAPSHOT)!";
  exit 1
fi
rm -fr package
cd ..
mvn clean package
mkdir -p snapshot-previews/package
cp goby.jar snapshot-previews/package/
cp goby snapshot-previews/package/
cp -r config snapshot-previews/package/
cp -r models snapshot-previews/package/
cd snapshot-previews/
rm goby-${VERSION}.zip
rm -fr goby-${VERSION}
mv package goby-${VERSION}
ls -ltrh goby-${VERSION}
zip -r goby-${VERSION}.zip goby-${VERSION}