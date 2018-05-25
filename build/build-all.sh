#!/bin/sh
#
# build-all.sh Builds for neutral linux targets; OSX and TGZ.
#
# Ross McDonald <ross@bheap.co.uk>
#
ROOT_DIR=$(dirname $0)/..
VERSION=$1

if [ "$VERSION" != "" ]
  then
  cd $ROOT_DIR
  lein clean
  lein uberjar
  cd -
  sh $ROOT_DIR/build/build-tgz.sh $VERSION
  sh $ROOT_DIR/build/build-deb.sh $VERSION
  sh $ROOT_DIR/build/build-rpm.sh $VERSION
  sh $ROOT_DIR/build/build-osx.sh $VERSION
  sh $ROOT_DIR/build/build-docker.sh $VERSION
  sh $ROOT_DIR/build/build-nix.sh $VERSION
else
  echo "ERROR: VERSION variable not defined"
  echo "Usage: $(basename "$0") VERSION"
fi
