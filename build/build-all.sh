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
  rm -rf target
  lein clean
  lein uberjar
  cd -
  sh $ROOT_DIR/build/build-tgz.sh $VERSION
  sh $ROOT_DIR/build/build-deb.sh $VERSION
  sh $ROOT_DIR/build/build-rpm.sh $VERSION
  sh $ROOT_DIR/build/build-osx.sh $VERSION
  sh $ROOT_DIR/build/build-docker.sh $VERSION
  sh $ROOT_DIR/build/build-nix.sh $VERSION
  echo "-----------------------------------------------------------------------"
  echo "Upload target/github/* to github release page"
  mkdir $ROOT_DIR/target/github
  cp $ROOT_DIR/target/tgz/protean.tgz $ROOT_DIR/target/github/
  cp $ROOT_DIR/target/protean_${VERSION}_all.deb $ROOT_DIR/target/github/
  cp $ROOT_DIR/target/rpmbuild/RPMS/noarch/protean-${VERSION}-develop.noarch.rpm $ROOT_DIR/target/github/
  cp $ROOT_DIR/target/osx/protean-osx.tgz $ROOT_DIR/target/github/
  cp $ROOT_DIR/target/nix/protean-nix.tgz $ROOT_DIR/target/github/
else
  echo "ERROR: VERSION variable not defined"
  echo "Usage: $(basename "$0") VERSION"
fi
