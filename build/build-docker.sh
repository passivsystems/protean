#!/bin/sh
#
# build-tgz.sh Builds the protean tgz binary package.
# A self contained build you can place anywhere.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
ROOT_DIR=$(dirname $0)/..

mkdir -p $ROOT_DIR/target/docker
cp $ROOT_DIR/target/*standalone* $ROOT_DIR/target/docker/protean.jar
rm -rf $ROOT_DIR/silk_templates/data/protean-api/*
rm -rf $ROOT_DIR/silk_templates/site/*
cp -r $ROOT_DIR/silk_templates $ROOT_DIR/target/docker
cp -r $ROOT_DIR/public $ROOT_DIR/target/docker
cp -r $ROOT_DIR/test-data $ROOT_DIR/target/docker
cp $ROOT_DIR/defaults.edn $ROOT_DIR/target/docker
cp $ROOT_DIR/sample-petstore.cod.edn $ROOT_DIR/target/docker
cp $ROOT_DIR/protean-utils.cod.edn $ROOT_DIR/target/docker
cp $ROOT_DIR/protean-utils.sim.edn $ROOT_DIR/target/docker
cp $ROOT_DIR/build/docker/* $ROOT_DIR/target/docker
cd $ROOT_DIR/target/docker
tar cvzf protean.tgz *
cd -
