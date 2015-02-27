#!/bin/sh
#
# build-tgz.sh Builds the protean tgz binary package for Build Monkey.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
ROOT_DIR=$(dirname $0)/..

mkdir -p $ROOT_DIR/target/bm
cp $ROOT_DIR/target/*standalone* $ROOT_DIR/target/bm
cp -r $ROOT_DIR/build/etc/* $ROOT_DIR/target/bm
cp -r $ROOT_DIR/public $ROOT_DIR/target/bm
cp -r $ROOT_DIR/test-data $ROOT_DIR/target/bm
# tidy up spin artefacts before packaging silk templates
rm -rf $ROOT_DIR/silk_templates/data/protean-api/*
rm -rf $ROOT_DIR/silk_templates/site/*
cp -r $ROOT_DIR/silk_templates $ROOT_DIR/target/bm
cp $ROOT_DIR/defaults.edn $ROOT_DIR/target/bm
cp $ROOT_DIR/simlib.clj.sample $ROOT_DIR/target/bm/simlib.clj
cp $ROOT_DIR/sample-petstore.cod.edn $ROOT_DIR/target/bm
cp $ROOT_DIR/protean-utils.cod.edn $ROOT_DIR/target/bm
cp $ROOT_DIR/protean-utils.sim.edn $ROOT_DIR/target/bm
cd $ROOT_DIR/target/bm
tar cvzf protean.tgz *
cd -
