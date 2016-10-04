#!/usr/bin/env bash

VERSION=$1
RELEASE_FOLDER="release-goby_${VERSION}"

rm -rf ${RELEASE_FOLDER}
mkdir -p ${RELEASE_FOLDER}
mvn clean
# we first assemble a clean base dir
mvn -pl :goby-framework assembly:single@make-goby-src

mvn install

mvn -pl :goby-framework assembly:single@make-goby-models
mvn -pl :goby-framework assembly:single@make-goby-data
mvn -pl :goby-framework assembly:single@make-goby-deps
mvn -pl :goby-framework assembly:single@make-goby-goby
mvn -pl :goby-framework assembly:single@make-goby-apidoc

mv target/*.zip  ${RELEASE_FOLDER}

cp goby.jar ${RELEASE_FOLDER}/goby.jar
cp CHANGES.txt ${RELEASE_FOLDER}
echo "${VERSION}" >> ${RELEASE_FOLDER}/VERSION.txt
(cd ${RELEASE_FOLDER}; ln -s *-data.zip goby-data.zip)
(cd ${RELEASE_FOLDER}; ln -s *-deps.zip goby-deps.zip)
(cd ${RELEASE_FOLDER}; ln -s *-models.zip goby-models.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-apidoc.zip goby-apidoc.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-src.zip goby-src.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-goby.zip goby.zip)