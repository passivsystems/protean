#!/bin/sh
#
# Installs protean with NIX.
#

#nix-env -i -f ../../build/nix/default.nix
nix-env -i -f https://github.com/passivsystems/protean/releases/download/0.11.0/protean-nix.tgz
