#!/bin/sh
#
# build-tgz.sh Builds the protean tgz binary package.
# A self contained build you can place anywhere.
# Must be run from the current directory as paths are relative.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
mkdir -p ../target/tgz
cp ../target/*standalone* ../target/tgz/protean.jar
rm -rf ../silk_templates/data/protean-api/*
rm -rf ../silk_templates/site/*
cp -r ../silk_templates ../target/tgz
cp -r ../public ../target/tgz
cp -r ../test-data ../target/tgz
cp ../defaults.edn ../target/tgz
cp ../simlib.clj.sample ../target/tgz/simlib.clj
cp ../sample-petstore.cod.edn ../target/tgz
cp ../protean-utils.cod.edn ../target/tgz
cp ../protean-utils.sim.edn ../target/tgz
cp tgz/* ../target/tgz
cd ../target/tgz
tar cvzf protean.tgz *
cd ../../build
