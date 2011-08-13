This is the event explorer subsystem originally developed by Ashish Mittal as part of \
the GSoC 2010 work.


It contains an ActiveMQ client listener and a UI.

Setup
-----
Download Cassandra to get some API libraries.
http://cassandra.apache.org/download/

Run these commands to add the needed Cassandra libraries to your local maven.
Note: be sure to use the versions that match your download.  The versions used at the time
of this writing are shown in the commands below and must be updated in pom.xml found with
this file.


mvn install:install-file -Dfile=lib/apache-cassandra-0.6.5.jar \
  -DgroupId=org.apache.cassandra.thrift -DartifactId=apache-cassandra -Dversion=0.6.5 \
  -DgeneratePom=true -Dpackaging=jar


Building
--------

mvn clean install


Running
-------

java -jar explorer/app/target/org.sakaiproject.nakamura.eventexplorer.app-1.0-sources.jar -p 8081 -f -

Logging will come out to screen and the http service will be on 8081. 
Nakamura is configured to listen on 8080, so this avoids a conflict.


