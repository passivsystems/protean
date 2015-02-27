#!/bin/sh
#
# build-rpm.sh Builds the protean rpm binary package.
#
# Ross McDonald <ross@bheap.co.uk>
#

# Check that CI variables exist in env
ROOT_DIR=$(dirname $0)/..
VERSION=$1

if [ "$VERSION" != "" ]
then
    # Assumes that lein uberjar has been run and uberjar exists in target directory
    cd $ROOT_DIR
    mkdir -p /rpmbuild/{RPMS,BUILD,BUILDROOT}
    rpmbuild --define 'version '$1 --define 'release develop' --define "_topdir $PWD/rpmbuild" -bb build/rpm/protean.spec --target noarch
    cd -
else
    echo "ERROR: VERSION variable not defined"
    echo "Usage: $(basename "$0") VERSION"
fi
