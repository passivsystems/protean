#!/bin/sh
#
# build-tgz.sh Builds the protean tgz binary package.
# A self contained build you can place anywhere.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
ROOT_DIR=$(dirname $0)/..

mkdir -p $ROOT_DIR/target/tgz
cp $ROOT_DIR/target/*standalone* $ROOT_DIR/target/tgz/protean.jar
rm -rf $ROOT_DIR/silk_templates/data/protean-api/*
rm -rf $ROOT_DIR/silk_templates/site/*
cp -r $ROOT_DIR/silk_templates $ROOT_DIR/target/tgz
cp -r $ROOT_DIR/public $ROOT_DIR/target/tgz
cp $ROOT_DIR/defaults.edn $ROOT_DIR/target/tgz
cp $ROOT_DIR/build/tgz/* $ROOT_DIR/target/tgz
cd $ROOT_DIR/target/tgz
tar cvzf protean.tgz *

# copy sha256 value to build/nix/from-jar.nix
cat protean.tgz | sha256sum > sha256.txt

cd -
