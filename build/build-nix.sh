#!/bin/sh
#
# build-osx.sh Builds the protean tgz binary package.
# Used by the custom OSX tgz based install script.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Assumes that lein uberjar has been run and uberjar exists in target directory
ROOT_DIR=$(dirname $0)/..
TARGET_DIR=$ROOT_DIR/target/nix/protean-0.12.0-pre.1



# Assumes that lein uberjar has been run and uberjar exists in target directory
rm -rf $ROOT_DIR/target/nix
mkdir -p $TARGET_DIR/lib

cp $ROOT_DIR/target/*standalone* $TARGET_DIR/lib/protean.jar
cp $ROOT_DIR/defaults.edn TARGET_DIR/lib/defaults.edn
cp $ROOT_DIR/sample-petstore.cod.edn $TARGET_DIR/lib/sample-petstore.cod.edn
cp $ROOT_DIR/protean-utils.cod.edn $TARGET_DIR/lib/protean-utils.cod.edn
cp $ROOT_DIR/protean-utils.sim.edn $TARGET_DIR/lib/protean-utils.sim.edn
rm -rf $ROOT_DIR/silk_templates/data/protean-api/*
rm -rf $ROOT_DIR/silk_templates/site/*
cp -r $ROOT_DIR/silk_templates $TARGET_DIR/lib/silk_templates
cp -r $ROOT_DIR/test-data $TARGET_DIR/lib/test-data
cp -r $ROOT_DIR/public $TARGET_DIR/lib

cd $ROOT_DIR/target/nix
tar cvzf protean-0.12.0-pre.1.tgz *
cd -

nix-build --keep-failed --expr 'with import <nixpkgs> {}; callPackage ./nix/default.nix {}'

# how to delete build after successful build?
# currently: rm result; nix-store --gc
# answer: nix-store --delete /nix/store/path
