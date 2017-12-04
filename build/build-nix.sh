#!/bin/sh
#
# build-osx.sh Builds the protean tgz binary package.
# Used by the custom OSX tgz based install script.
#
# Ross McDonald <ross@bheap.co.uk>
#


nix-build --keep-failed --expr 'with import <nixpkgs> {}; callPackage ./nix/default.nix {}'

# how to delete build after successful build?
# currently: rm result; nix-store --gc
# answer: nix-store --delete `readlink result` --ignore-liveness
