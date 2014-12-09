#!/bin/sh
#
# build-rpm.sh Builds the protean rpm binary package.
# Must be run from the current directory as paths are relative.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Check that CI variables exist in env
if [ $1 != "" ]
then
    # Assumes that lein uberjar has been run and uberjar exists in target directory
    cd ..
    mkdir -p rpmbuild/{RPMS,BUILD,BUILDROOT}
    rpmbuild --define 'version '$1 --define 'release develop' --define "_topdir $PWD/rpmbuild" -bb build/rpm/protean.spec --target noarch
    cd build
else
    echo "ERROR: VERSION variable not defined"
fi
