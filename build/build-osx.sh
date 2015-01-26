#!/bin/sh
#
# build-osx.sh Builds the protean tgz binary package.
# Used by the custom OSX tgz based install script.
# Must be run from the current directory as paths are relative.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
mkdir -p ../target/osx
cp ../target/*standalone* ../target/osx/protean.jar
cp -r ../silk_templates ../target/osx
cp -r ../public ../target/osx
cp -r ../test-data ../target/osx
cp ../defaults.edn ../target/osx
cp ../sample-petstore.cod.edn ../target/osx
cp ../protean-utils.cod.edn ../target/osx
cp ../protean-utils.sim.edn ../target/osx
cp osx/* ../target/osx
cd ../target/osx
tar cvzf protean-osx.tgz *
cd ../../build
