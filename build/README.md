BUILD INSTRUCTIONS

This file details builds across various deployment platforms.
All builds assume that lein uberjar has been run and the uberjar exists in the target directory.

=====
Linux
=====

DOCKER
------

(assuming you are in the build directory)
./build-docker.tgz
cd ../target/docker
docker rmi -f protean-example
docker build -t protean-example .

Test your new Docker image with :
docker run -it -p 3000:3000 -p 3001:3001 protean-example


TGZ
---

(assuming you are in the build directory)
(substitute your version for the example below)
./build-tgz.sh 0.5.0


RPM
---

(assuming you are in the build directory)
(substitute your version for the example below)
./build-rpm.sh 0.5.0


DEB
---

(assuming you are in the build directory)
(substitute your version for the example below)
./build-deb.sh 0.5.0


===
OSX
===

(assuming you are in the build directory)
(substitute your version for the example below)
./build-osx.sh 0.5.0
