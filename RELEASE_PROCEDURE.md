# Goby Release Procedure

This page describes the process for making a formal software release of the [Goby](http://goby.campagnelab.org) project.

A combination of bash scripts and maven goals exist to aid in the process of bundling a new release for public distribution. The scripts are available in the ''formal-releases'' directory of the Goby source tree.

Once the development team is confident that the source code is stable, the binary distribution, the javadoc and associated data, models and configuration files must be tagged, bundled and uploaded to the [Goby](http://goby.campagnelab.org) web site for distribution.
## Access to the repository

The user creating the release must have write access to Goby in github.
```sh
git clone git@github.com:campagnelaboratory/goby3.git
```
## Deploy to Maven Central Repository

The goby-io and goby-framework modules must be published to the Central Repository. In order to do that, the following configuration must be set on the local machine:
 - pgp keys to digitally sign the artifacts
 - campagnelaboratory's user on sonatype to push the artifacts on the Sonatype repos.

```sh
  $ cd goby3
  $ mvn deploy
```

The 4 artifacts (goby-framework, goby-distribution, goby-io and goby-spi) are published in the Nexus Staging repository. 

Log in to https://oss.sonatype.org/#stagingRepositories as ''campagnelaboratory'' and search for a repository called "campagnelab-XXXX". Select the repo and then open the Summary tab in the bottom panel of the page. 

We usually want to publish only goby-io and goby-framework, therefore we right click on goby-distribution and goby-spi and remove them from the staged artifacts.

A good practice is to test the staged artifacts as dependencies in another Maven-based project to check if dependency tree is correctly resolved.

[TBP: how to enable the staging repo for deps resolution]


### How to approve and release

Log in to https://oss.sonatype.org/#stagingRepositories as ''campagnelaboratory'' and search for a repository called "campagnelab-XXXX". Select the repo and then click on the Release button located above the list of repositories. After some time (usually 10 minutes), the repository will disappear. This means that the release has been approved by Sonatype and from now on the artifacts are available on the Central Repository. It will take between 20 mins to few hours to be indexed by Central Repository. After that period, they should be available in the org.campagnelab groupID (http://search.maven.org/#search%7Cga%7C1%7Corg.campagnelab). 
## Prepare the release files
```sh
  $ cd goby3/formal-releases
  $ ./prepare-release.sh
```

The version of the release is set according to the goby-framework version declared in the goby3/pom.xml. Snapshot versions are not accepted for formal releases.

The release script will tag the Goby project in the subversion repository so that the contents of the release can be tracked back to the source.  The script only bundles what is in the main trunk of the subversion repository and NOT what exists on the local disk.  This is intentional since it is essential that we know exactly what is in the distribution.

The files intended for distribution will be placed into a directory called release-goby_VERSION.  An example of a release structure using the tag '''3.0.2''' is as follows:
```sh
   $ ls -1 release-goby_3.0.2/
    goby.jar
    CHANGES.txt
    VERSION.txt
    goby_3.0.2-javadoc.zip
    goby_3.0.2-bin.zip
    goby_3.0.2-deps.zip
    goby_3.0.2-data.zip
    goby_3.0.2-models.zip
    goby-bin.zip -> goby_3.0.2-bin.zip
    goby-data.zip -> goby_3.0.2-data.zip
    goby-deps.zip -> goby_3.0.2-deps.zip
    goby-javadoc.zip -> goby_3.0.2-javadoc.zip
    goby-models.zip -> goby_3.0.2-models.zip
```


The bin file contains the binary distribution of Goby. You can unpack only this file if you wish to use Goby from the command line.

The models, data, and deps files contain other files used to create Goby. The javadoc file includes javadoc documentation for programmers that wish to extend/interface the Goby source.

Note the presence of softlinks that use a generic name to the specific version file.  These are set up so that the download site does need to change every time a new release is made.

## Push the release

Before sending the release out, some testing should be performed. Open and execute each of the jar files and run a few examples. Once you are happy with the content of the release folder, you can decide to push the release of the  web site for distribution.

At this point, the release files need to be placed on the web server. This requires access to the account "www" on okeeffe. To push the release, just execute the following commands:


```sh
  $ cd goby3/formal-releases
  $ ./push-release.sh
```

If everything works, the entire Goby release directory is be copied to /var/www/dirs/chagall/goby/releases/ on okeeffe and a latest-release symlink is created in the folder to link the new release folder. We suggest to manually inspect the new release folder before moving to the next step.

## Tag the release
The final step is to tag the release in the git repository. 
```sh
  $ cd goby3/formal-releases
  $ ./tag-release.sh
```

The release is tagged as ''r{version}'', where version is the goby-framework version declared in the goby3/pom.xml