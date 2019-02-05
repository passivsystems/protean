#!/bin/sh
#
# Installs Protean on OSX

sudo mkdir /usr/local/Cellar/protean
sudo mv {defaults.edn,public,silk_templates,protean*} /usr/local/Cellar/protean/
sudo ln -s /usr/local/Cellar/protean/protean /usr/local/bin/
