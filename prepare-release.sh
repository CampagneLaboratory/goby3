#!/usr/bin/env bash

VERSION="3.0.2"
RELEASE_FOLDER="release-goby_${VERSION}"

mkdir -p ${RELEASE_FOLDER}
#mvn -pl :goby-distribution clean package
cp goby-distribution/target/*-javadoc.jar ${RELEASE_FOLDER}/goby_${VERSION}-apidoc.zip
cp goby-distribution/target/*-sources.jar ${RELEASE_FOLDER}/goby_${VERSION}-src.zip

mvn -pl :goby-framework assembly:single@make-goby-models
mvn -pl :goby-framework assembly:single@make-goby-data

mv target/*.zip  ${RELEASE_FOLDER}

(cd ${RELEASE_FOLDER}; ln -s *-data.zip goby-data.zip)
(cd ${RELEASE_FOLDER}; ln -s *-models.zip goby-models.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-apidoc.zip goby-apidoc.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-src.zip goby-src.zip)