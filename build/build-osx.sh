#!/bin/sh
#
# build-osx.sh Builds the protean tgz binary package.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
ROOT_DIR=$(dirname $0)/..

mkdir -p $ROOT_DIR/target/osx/protean
cp $ROOT_DIR/target/*standalone* $ROOT_DIR/target/osx/protean/protean.jar
rm -rf $ROOT_DIR/silk_templates/data/protean-api/*
rm -rf $ROOT_DIR/silk_templates/site/*
cp -r $ROOT_DIR/silk_templates $ROOT_DIR/target/osx/protean
cp -r $ROOT_DIR/public $ROOT_DIR/target/osx/protean
cp $ROOT_DIR/defaults.edn $ROOT_DIR/target/osx/protean
cp $ROOT_DIR/build/osx/* $ROOT_DIR/target/osx/protean
cd $ROOT_DIR/target/osx
tar cvzf protean-osx.tgz *
cd -
