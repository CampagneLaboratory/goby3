# Goby Release Procedure

This page describes the process for making a formal software release of the [Goby 3](http://goby.campagnelab.org) project.

A combination of bash scripts and maven goals exist to aid in the process of bundling a new release for public distribution. The scripts are available in the ''formal-releases'' directory of the Goby3 project.

Once the development team is confident that the source code is stable, the binary distribution, the javadoc and associated data, models and configuration files must be tagged, bundled and uploaded to the [Goby](http://goby.campagnelab.org) web site for distribution.

## Requirements:
 
- [Java 1.8+](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
- [Maven 3.3.5+](https://maven.apache.org/download.cgi)
- git 1.8+ client
- tar and zip commands
- write access to [Goby 3](https://github.com/CampagneLaboratory/goby3) on github
- gpg keys of Campagne laboratory
- credentials for logging in to Nexus with the Campagne Lab's user

## Clone to the repository
```sh
  git clone git@github.com:campagnelaboratory/goby3.git
```
## Deploy to Maven Central

The goby artifacts produced by the project's modules must be published into the [Central Repository](http://search.maven.org/). In order to do that, the following configuration must be available on the local machine:
 
 - pgp keys to digitally sign the artifacts
 - Campagne Lab's user on sonatype to push the artifacts on the Sonatype repos.

These are the steps required by Sonatype to deploy artifacts into the Central Repository:

 - Deploy snapshot artifacts into repository [https://oss.sonatype.org/content/repositories/snapshots](https://oss.sonatype.org/content/repositories/snapshots)
 - Deploy release artifacts into the staging repository [https://oss.sonatype.org/service/local/staging/deploy/maven2](https://oss.sonatype.org/service/local/staging/deploy/maven2)
 - Promote staged artifacts into repository 'Releases'
 - Download release artifacts from group [https://oss.sonatype.org/content/groups/public](https://oss.sonatype.org/content/groups/public)

Once built, goby3 generates 4 artifacts (goby-framework, goby-distribution, goby-io and goby-spi), but we release only goby-framework and goby-io in the Central Repository.
### Publish in the snapshot repository
It's a good practice to test the deployment in the shapshot repository at Sonatype beforehand. The snapshot repository is automatically selected as deployment target when the pom version has the -SNAPSHOT suffix.

To publish the shapshot artifacts, type the following command in the root folder of the cloned project (goby3):
```sh
  mvn deploy
```
To inspect the repository, log into the Nexus Repository Manager in the [Snapshot Repositories](https://oss.sonatype.org/#view-repositories;snapshots~browsestorage) area with the Campagne Lab's user. In the bottom panel, select the Browse Storage tab and lookup for 'org/campagnelab/goby'. The tree below will open on the Campagne lab's snapshot area and we should be able to see the newly deployed shapshot artifacts.

Assuming we are deploying a snapshot with version 3.0.3-SNAPSHOT, we can test that the goby-io artifact is correctly deployed and its dependencies are resolved with the following command:
```sh
mvn org.apache.maven.plugins:maven-dependency-plugin:2.10:get \
    -DremoteRepositories=https://oss.sonatype.org/content/repositories/snapshots \
    -Dtransitive=true \
    -Dartifact=org.campagnelab.goby:goby-io:3.0.3-SNAPSHOT
```

### Publish in a temporary staging repository

Once you are satisfied with the snapshot artifacts, remove the -SNAPSHOT suffix from any pom in the project and type the following command in the root folder (goby3):
```sh
  mvn clean deploy
```

The deploy phase publishes the 4 artifacts of the project (goby-framework, goby-distribution, goby-io and goby-spi) in a new temporary staging repository at Nexus Staging repository.

To view the repository, log into [Staging Repositories](https://oss.sonatype.org/#stagingRepositories) with the Campagne Lab's user and search for a repository called "campagnelab-XXXX". Select the repo and then open the Summary tab in the bottom panel of the page.

Since we want to publish only goby-io and goby-framework, we right click on goby-distribution and goby-spi and remove both of them from the staged artifacts.

### Test the staged release
Before approving the release, you have to test that the staged artifacts and their dependencies would be correctly resolved if used in another project.
For that we don't need to set up another Maven-based project.

Assuming we are staging version 3.0.3, the following line command can be used to test the goby-io artifact and its dependencies:

```sh
mvn org.apache.maven.plugins:maven-dependency-plugin:2.10:get \
    -DremoteRepositories=https://oss.sonatype.org/service/local/staging/deploy/maven2 \
    -Dtransitive=true \
    -Dartifact=org.campagnelab.goby:goby-io:3.0.3
```

### Approve and release

Log into [Staging Repositories](https://oss.sonatype.org/#stagingRepositories) with the Campagne Lab's user and search for a repository called "campagnelab-XXXX". Select the repo and then click on the Release button located above the list of repositories. After some time (usually about 10 minutes), the repository should disappear. This means that the release has been approved by Sonatype and from now on the artifacts are available on the Central Repository. It will take between 20 mins to few hours to be also indexed by Central Repository. After that period, they should be available in the [org.campagnelab groupID](http://search.maven.org/#search%7Cga%7C1%7Corg.campagnelab).

_Do note that the approval cannot be reverted. Once approved and released, the artifacts CANNOT be deleted from the Central Repository._
### Test the approved release
As for the staged artifact, we check that the release and its dependencies are resolved as expected with the following command:
```sh
 mvn org.apache.maven.plugins:maven-dependency-plugin:2.10:get \
    -DremoteRepositories=https://oss.sonatype.org/content/groups/public \
    -Dtransitive=true \
    -Dartifact=org.campagnelab.goby:goby-io:3.0.3
```

## Prepare a snapshot release
1. Build goby and push the jars to the local maven repo (i.e., mvn install).
2. Build variationanalysis (run build-cpu.sh to also install those jars)
3. Run the snapshot script to bundle these elements together:

```sh
  cd snapshot-previews
  /prepare-preview.sh [GOBY.VERSION]-SNAPSHOT [VARIATIONANALYSIS.VERSION]-SNAPSHOT
```

## Prepare the release files
```sh
  cd goby3/formal-releases
  ./prepare-release.sh [VARIATIONANALYSIS.VERSION]
```

The version of the release is set according to the goby-framework version declared in the goby3/pom.xml. Snapshot versions are not accepted for formal releases.

The release script will tag the Goby project in the subversion repository so that the contents of the release can be tracked back to the source.  The script only bundles what is in the main trunk of the subversion repository and NOT what exists on the local disk.  This is intentional since it is essential that we know exactly what is in the distribution.

The files intended for distribution will be placed into a directory called release-goby_VERSION.  An example of a release structure is as follows:
```sh
   ls -1 release-goby_3.0.3/
    goby.jar
    CHANGES.txt
    VERSION.txt
    goby_3.0.3-javadoc.zip
    goby_3.0.3-bin.zip
    goby_3.0.3-deps.zip
    goby_3.0.3-data.zip
    goby_3.0.3-models.zip
    goby-bin.zip -> goby_3.0.3-bin.zip
    goby-data.zip -> goby_3.0.3-data.zip
    goby-deps.zip -> goby_3.0.3-deps.zip
    goby-javadoc.zip -> goby_3.0.3-javadoc.zip
    goby-models.zip -> goby_3.0.3-models.zip
```


The bin file contains the binary distribution of Goby. You can unpack only this file if you wish to use Goby from the command line.

The models, data, and deps files contain other files used to create Goby. The javadoc file includes javadoc documentation for programmers that wish to extend/interface the Goby source.

Note the presence of softlinks that use a generic name to the specific version file.  These are set up so that the download site does need to change every time a new release is made.

## Push the release

Before sending the release out, some testing should be performed. Open and execute each of the jar files and run a few examples. Once you are happy with the content of the release folder, you can decide to push the release on the web site for distribution.

At this point, the release files need to be placed on the web server. This requires access to the account "www" on okeeffe. To push the release, just execute the following commands:


```sh
  cd goby3/formal-releases
  ./push-release.sh
```

If everything works, the entire Goby release directory is be copied to /var/www/dirs/chagall/goby/releases/ on okeeffe and a latest-release symlink is created in the folder to link the new release folder. We suggest to manually inspect the new release folder before moving to the next step.

## Tag the release
The final step is to tag the release in the git repository:
```sh
   cd goby3/formal-releases
   ./tag-release.sh
```
The release is tagged as ''r{version}'', where version is the goby-framework version declared in the goby3/pom.xml