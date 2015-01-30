#!/bin/sh
#
# release.sh Builds for neutral linux targets; OSX and TGZ.
# Must be run from the current directory as paths are relative.
#
# Ross McDonald <ross@bheap.co.uk>
#

if [ $1 != "" ]
  then
  cd ..
  lein clean
  lein uberjar
  cd -
  sh build-tgz.sh $1
  sh build-osx.sh $1
else
  echo "ERROR: VERSION variable not defined"
fi
