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

[TODO]

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

At this point, the release files need to be placed on the web server. This requires access to the account "www":


```sh
  $ cd goby3/formal-releases
  $ ./push-release.sh
```
The entire Goby release directory will be copied to /var/www/dirs/chagall/goby/releases/ on okeeffe.

[TBC]