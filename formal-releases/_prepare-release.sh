#!/usr/bin/env bash

VERSION=$1
BASEDIR=$2
WORKDIR=`pwd`
RELEASE_FOLDER="${WORKDIR}/release-goby_${VERSION}"

rm -rf ${RELEASE_FOLDER}
mkdir -p ${RELEASE_FOLDER}
cd ${BASEDIR}

#clean up before preparing the archives
mvn clean
rm -f ${BASEDIR}/goby.jar

# we first assemble a clean base dir
#mvn -pl :goby-framework assembly:single@make-goby-src

mvn install

mvn -pl :goby-framework assembly:single@make-goby-models
mvn -pl :goby-framework assembly:single@make-goby-data
mvn -pl :goby-framework assembly:single@make-goby-deps
mvn -pl :goby-framework assembly:single@make-goby-bin
mvn -pl :goby-framework assembly:single@make-goby-javadoc
#mvn -pl :goby-framework assembly:single@make-goby-cpp

cd ${WORKDIR}
# move the generated files to the release folder
cp ${BASEDIR}/CHANGES.txt ${RELEASE_FOLDER}
echo "${VERSION}" >> ${RELEASE_FOLDER}/VERSION.txt
mv ${BASEDIR}/target/goby_${VERSION}-*.zip  ${RELEASE_FOLDER}
cp ${BASEDIR}/goby.jar ${RELEASE_FOLDER}/goby.jar

# create symlinks
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-data.zip goby-data.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-deps.zip goby-deps.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-models.zip goby-models.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-javadoc.zip goby-javadoc.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-bin.zip goby-bin.zip)
#(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-src.zip goby-src.zip)
#(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-cpp.zip goby-cpp.zip)