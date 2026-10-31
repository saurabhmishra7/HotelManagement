#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test -f target/innovaserve.jar
mkdir -p target/appsail
cp target/innovaserve.jar target/appsail/innovaserve.jar
# Only this staging folder is uploaded; not tests, credentials or the full target tree.
if jar tf target/appsail/innovaserve.jar | grep -E '(application[.-](dev|local)\.properties|\.env|\.(pem|p12|jks)$)'; then
 echo 'Local configuration found in deployment artifact' >&2; exit 1
fi
