#!/bin/sh
#
# build-osx.sh Builds the protean tgz binary package.
# Used by the custom OSX tgz based install script.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
ROOT_DIR=$(dirname $0)/..

mkdir -p $ROOT_DIR/target/osx
cp $ROOT_DIR/target/*standalone* $ROOT_DIR/target/osx/protean.jar
rm -rf $ROOT_DIR/silk_templates/data/protean-api/*
rm -rf $ROOT_DIR/silk_templates/site/*
cp -r $ROOT_DIR/silk_templates $ROOT_DIR/target/osx
cp -r $ROOT_DIR/public $ROOT_DIR/target/osx
cp $ROOT_DIR/defaults.edn $ROOT_DIR/target/osx
cp $ROOT_DIR/sample-petstore.cod.edn $ROOT_DIR/target/osx
cp $ROOT_DIR/protean-utils.cod.edn $ROOT_DIR/target/osx
cp $ROOT_DIR/protean-utils.sim.edn $ROOT_DIR/target/osx
cp $ROOT_DIR/build/osx/* $ROOT_DIR/target/osx
cd $ROOT_DIR/target/osx
tar cvzf protean-osx.tgz *
cd -
