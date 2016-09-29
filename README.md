Goby is a data management framework designed to facilitate the implementation of efficient data analysis pipelines for high-throughput sequencing data.
The software has been under development and released since 2010.

In June 2012, we released Goby 2.0, a version with compression methods that provide state of the art compression of High-Throughput Sequencing (HTS) alignment data.

In September 2016, we released Goby 3.0, which incorporates probabilistic models trained with deep learning approaches.

### Goby 1 and 2
You found the Goby3 repository. This repository was cut from the first
Goby repo (with Goby 1 and 2) to keep the repo small.
The original repo can be found [here](github.com/CampagneLaboratory/goby).

### File formats
Goby provides very efficient file formats to store next-generation sequencing data and intermediary analysis results.
### Framework
Goby is a framework to help bioinformaticians program efficient analysis tools quickly. The framework was engineered for performance and flexibility. Tools written with Goby often have much better performance and scalability compared to programs developed with other approaches.
### Algorithms
Goby provides efficient algorithms for most computational tasks required when analyzing HTS data. For instance, an ultra-fast local realignment around indels algorithm works directly with Goby HTS alignments and can realign reads on the fly as the alignment is read.
### Authors and Contributors
Goby is currently being developed by the members of the [Campagne laboratory](http://campagnelab.org).
### Source code
Goby source code is now on GitHub.  You can obtain and build the project as follows:
   ```
   git clone git://github.com/CampagneLaboratory/goby3.git;
   
   cd goby3
   ```
#### Compilation:
   ```
   mvn install
   ```
   This should create a goby.jar file in the goby3 folder, which can be used to run goby.
#### Running
   After compilation, we recommend running goby with the wrapper:
   ```
   <installation directory>/goby 1g --help
   ```
The wrapper sets some variables, and runs the jar file. You can also run goby directly from the JAR file:
   ```
   java -jar goby.jar -Xmx1g --help
   ```
In this case, adjust the -Xmx1g according to the amount of memory you need. You should also defined the GOBY_HOME environment variable to point to the directory that contains the goby distribution or repo.
### Documentation and forums
You will find extensive documentation at [goby.campagnelab.org](http://goby.campagnelab.org).
Usage questions and feedback should be addressed to the [Goby user forum](https://groups.google.com/forum/?fromgroups#!forum/goby-framework).

If you have questions about compiling the software, reach us on Gitter:

[![Join the chat at https://gitter.im/CampagneLaboratory/goby](https://badges.gitter.im/CampagneLaboratory/goby.svg)](https://gitter.im/CampagneLaboratory/goby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
