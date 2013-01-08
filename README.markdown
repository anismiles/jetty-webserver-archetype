A maven archetype to help us quickly create and start on new API projects. Our tech stack is Google Guice, Resteasy and Embedded Jetty. Read more: http://anismiles.wordpress.com/2013/01/08/jetty-executable-webserver-archetype

## Install

You can either build/install from source 

git clone https://github.com/anismiles/jetty-webserver-archetype
mvn clean install

Or you can simply download the jar and install that. 

mvn install:install-file \
-Dfile=jetty-webserver-archetype-1.0.jar \
-DgroupId=com.strumsoft \
-DartifactId=jetty-webserver-archetype \
-Dversion=1.0 \
-Dpackaging=jar \ 
-DgeneratePom=true

## Usage

Let’s say you want to create a new project “hello-world” with group “com.hello.world”, you can run:

mvn archetype:generate \
-DgroupId=com.hello.world \
-DartifactId=hello-world \
-Dversion=1.0-SNAPSHOT \
-DarchetypeGroupId=com.strumsoft \
-DarchetypeVersion=1.0 \
-DarchetypeArtifactId=jetty-webserver-archetype

That’s it. You are ready to roll. You have set up a basic working API app. Now, you can run it in dev mode 

mvn clean jetty:run

Or in production mode

mvn clean package
java –jar target/hello-world-1.0-SNAPSHOT-dist.war start & 

To stop:
java –jar target/hello-world-1.0-SNAPSHOT-dist.war stop

