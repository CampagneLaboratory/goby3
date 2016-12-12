#!/usr/bin/env bash

VERSION=$1
BASEDIR=$2
DL_VERSION=$3
WORKDIR=`pwd`
RELEASE_FOLDER="${WORKDIR}/release-goby_${VERSION}"


if [[ $VERSION == *"SNAPSHOT"* ]] ;then
  echo "Current version is set to ${VERSION}. Can't release a snapshot!";
  exit 1
fi

rm -rf ${RELEASE_FOLDER}
mkdir -p ${RELEASE_FOLDER}
cd ${BASEDIR}

#clean up before preparing the archives
mvn clean
rm -f ${BASEDIR}/goby.jar
rm -f ${BASEDIR}/somatic.jar
rm -f ${BASEDIR}/framework.jar

mvn install

mvn -pl :goby-framework assembly:single@make-goby-models
mvn -pl :goby-framework assembly:single@make-goby-data
mvn -pl :goby-framework assembly:single@make-goby-deps
mvn -pl :goby-framework assembly:single@make-goby-javadoc

# download variation analysis dependencies
mvn -f dl-downloader.xml dependency:copy -Ddl.version=${DL_VERSION} -U
cd ${WORKDIR}   ls
# move the generated files to the release folder
cp ${BASEDIR}/CHANGES.txt ${RELEASE_FOLDER}
echo "${VERSION}" >> ${RELEASE_FOLDER}/VERSION.txt
mv ${BASEDIR}/target/goby_${VERSION}-*.zip  ${RELEASE_FOLDER}
cp ${BASEDIR}/goby.jar ${BASEDIR}/somatic.jar ${BASEDIR}/framework.jar ${RELEASE_FOLDER}

#create goby-bin.zip
cd ${BASEDIR}
mvn -pl :goby-framework assembly:single@make-goby-bin
mv ${BASEDIR}/target/goby_${VERSION}-bin.zip  ${RELEASE_FOLDER}


# create symlinks
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-data.zip goby-data.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-deps.zip goby-deps.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-models.zip goby-models.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-javadoc.zip goby-javadoc.zip)
(cd ${RELEASE_FOLDER}; ln -s goby_${VERSION}-bin.zip goby.zip)