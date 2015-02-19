#!/bin/sh
#
# build-tgz.sh Builds the protean tgz binary package for Build Monkey.
# Must be run from the current directory as paths are relative.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
mkdir -p ../target/bm
cp ../target/*standalone* ../target/bm
cp -r etc/* ../target/bm
cp -r ../public ../target/bm
cp -r ../test-data ../target/bm
# tidy up spin artefacts before packaging silk templates
rm -rf ../silk_templates/data/protean-api/*
rm -rf ../silk_templates/site/*
cp -r ../silk_templates ../target/bm
cp ../defaults.edn ../target/bm
cp ../simlib.clj.sample ../target/bm/simlib.clj
cp ../sample-petstore.cod.edn ../target/bm
cp ../protean-utils.cod.edn ../target/bm
cp ../protean-utils.sim.edn ../target/bm
cd ../target/bm
tar cvzf protean.tgz *
cd ../../build
