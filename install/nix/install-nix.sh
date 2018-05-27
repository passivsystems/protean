#!/bin/sh
#
# Installs protean with NIX.
#

#nix-env -i -f ../../build/nix/default.nix
nix-env -i -f https://github.com/passivsystems/protean/releases/download/0.12.2/protean-nix.tgz
