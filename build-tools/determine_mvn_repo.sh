#!/bin/bash --noprofile

[[ -z ${GERRIT_VERSION} ]] && GERRIT_VERSION=$1

LIBS_SNAPSHOT="libs-snapshot-local"
LIBS_STAGING="libs-staging-local"

# Gerrit only supports pulling from non unique snapshot repos as it uses wget not maven to pull
# the snapshot references ( unless you use MAVEN_LOCAL as the repo source then its ok )  So for now
# only reference libs-staging here regardless of snapshot or not.

# We always push gerrit and console-api etc to libs-staging to allow us to pull the assets ok.
# Use artifactory promotion from our release tooling to move from libs-staging to libs-release.
[[ $GERRIT_VERSION =~ "-SNAPSHOT" ]] && echo $LIBS_STAGING || echo $LIBS_STAGING
