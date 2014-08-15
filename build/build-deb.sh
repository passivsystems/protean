#!/bin/sh
#
# build-deb.sh Builds the protean deb binary package.
# Must be run from the current directory as paths are relative.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Check that CI variables exist in env
if [ $1 != "" ]
then
    # Assumes that lein uberjar has been run and uberjar exists in target directory
    rm -rf ../target/deb
    mkdir -p ../target/deb
    mkdir -p ../target/deb/usr/bin
    mkdir -p ../target/deb/usr/lib/protean

    cp ../target/*standalone* ../target/deb/usr/lib/protean/protean.jar
    cp -r etc/protean ../target/deb/usr/bin

    # Copy over the DEBIAN directory
    cp -r DEBIAN ../target/deb
    # Change the control file to use CI variables from env
    cat DEBIAN/control | sed s/%VERSION%/$1/  > ../target/deb/DEBIAN/control

    cp -r etc ../target/deb

    dpkg-deb --build ../target/deb ../target
else
    echo "ERROR: VERSION variable not defined"
fi
