#!/usr/bin/env bash
# Creates a release keystore + keystore.properties (both git-ignored). Keep the .jks safe: you need
# the same key for every future update of the app.
set -euo pipefail
cd "$(dirname "$0")/.."
PASS=${1:-$(head -c 18 /dev/urandom | base64 | tr -d '/+=')}
keytool -genkeypair -v -keystore standup-release.jks -alias standup -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=StandUp, O=Personal, C=US"
cat > keystore.properties <<EOP
storeFile=standup-release.jks
storePassword=$PASS
keyAlias=standup
keyPassword=$PASS
EOP
echo "Created standup-release.jks and keystore.properties (password: $PASS)"
