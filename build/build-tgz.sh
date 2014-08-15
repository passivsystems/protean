#!/bin/sh
#
# build-tgz.sh Builds the protean tgz binary package.
# Used by the custom OSX tgz based install script.
# Must be run from the current directory as paths are relative.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
mkdir -p ../target/tgz
cp ../target/*standalone* ../target/tgz/protean.jar
cp tgz/protean ../target/tgz
cd ../target/tgz
tar cvzf protean.tgz *
cd ../../build
