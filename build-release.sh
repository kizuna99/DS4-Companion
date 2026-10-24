#!/usr/bin/env bash
# DS4 Companion - Release Build & Signing Script
# Builds and signs the release APK using the persistent upload keystore.
set -euo pipefail

KEYSTORE_FILE="${KEYSTORE_PATH:-./my-upload-key.jks}"
STORE_PASS="${STORE_PASSWORD:-ds4companion_release_2026}"
KEY_PASS="${KEY_PASSWORD:-ds4companion_release_2026}"

if [ ! -f "$KEYSTORE_FILE" ]; then
    echo "Error: Keystore file '$KEYSTORE_FILE' not found."
    echo "To generate a new keystore, run:"
    echo "  keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000 -dname \"CN=DS4 Companion, OU=Android Mobile, O=Independent Developer, C=ID\""
    exit 1
fi

echo "Building DS4 Companion Release APK..."
STORE_PASSWORD="$STORE_PASS" KEY_PASSWORD="$KEY_PASS" KEYSTORE_PATH="$KEYSTORE_FILE" gradle :app:assembleRelease

RELEASE_APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$RELEASE_APK" ]; then
    echo "----------------------------------------------------"
    echo "Release APK built successfully!"
    echo "Location: $RELEASE_APK"
    echo "Copying to root DS4_Companion.apk for direct distribution..."
    cp "$RELEASE_APK" ./DS4_Companion.apk
    echo "Done."
else
    echo "Build failed: output APK not found at $RELEASE_APK"
    exit 1
fi
