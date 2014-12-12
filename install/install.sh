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
     $HTTP_CLIENT https://github.com/passivsystems/protean/releases/download/0.9.0-alpha.4/protean-osx.tgz -o $HOME/bin/protean-osx.tgz
     tar xf $HOME/bin/protean-osx.tgz -C $HOME/bin
     rm $HOME/bin/protean-osx.tgz
}

download_tarball
