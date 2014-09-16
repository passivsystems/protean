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
cp ../sample-codex.edn ../target/bm
cd ../target/bm
tar cvzf protean.tgz *
cd ../../build
