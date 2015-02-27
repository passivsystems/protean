#!/bin/sh
#
# release.sh Builds for neutral linux targets; OSX and TGZ.
# Must be run from the current directory as paths are relative.
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
  sh $ROOT_DIR/build/build-tgz.sh $1
  sh $ROOT_DIR/build/build-osx.sh $1
else
  echo "ERROR: VERSION variable not defined"
  echo "Usage: $(basename "$0") VERSION"
fi
