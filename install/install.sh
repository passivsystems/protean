#!/bin/bash

# Check me out before you run me, I'm just gonna install Protean honest

# Borrowed from lein and emacs-live
HTTP_CLIENT=${HTTP_CLIENT:-"wget -O"}
if type -p curl >/dev/null 2>&1; then
    HTTP_CLIENT="curl $CURL_PROXY -f -k -L"
fi

# Create user home bin directory if it does not exist
if [ ! -d "$HOME/bin" ]; then
  mkdir -p $HOME/bin
fi

function download_tarball {
     echo ""
     echo $(tput setaf 2)"--> Downloading Protean..."$(tput sgr0)
     echo ""

     $HTTP_CLIENT https://github.com/passivsystems/protean-cli/releases/download/0.5.0/protean.tgz -o $HOME/bin/protean.tgz
     tar xf $HOME/bin/protean.tgz -C $HOME/bin
     rm $HOME/bin/protean.tgz
}

download_tarball
