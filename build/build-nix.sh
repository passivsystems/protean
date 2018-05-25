#!/bin/sh
#
# build-nix.sh Builds the protean nix install package.
# Used by the custom NIX tgz based install script.
#
# Ross McDonald <ross@bheap.co.uk>
#

tar cvzf protean-nix.tgz nix/
