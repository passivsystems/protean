#!/bin/sh
#
# build-nix.sh Builds the protean nix install package.
# Used by the custom NIX tgz based install script.
#
# Ross McDonald <ross@bheap.co.uk>
#


ROOT_DIR=$(dirname $0)/..

mkdir -p $ROOT_DIR/target/nix
cp nix/* $ROOT_DIR/target/nix

sha256="$(cat $ROOT_DIR/target/tgz/sha256.txt | awk '{print $1;}')"
sed -i -e "s/CHANGE_ME/$sha256/g" $ROOT_DIR/target/nix/from-jar.nix

tar cvzf $ROOT_DIR/target/nix/protean-nix.tgz $ROOT_DIR/target/nix/
