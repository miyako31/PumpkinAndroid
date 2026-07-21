#!/usr/bin/env bash
# =============================================================================
# generate_keystore.sh
#
# Generates an Android signing keystore and prints the values you need to
# register as GitHub Actions Secrets for signed release builds.
#
# Usage:
#   chmod +x scripts/generate_keystore.sh
#   ./scripts/generate_keystore.sh
# =============================================================================

set -euo pipefail

KEYSTORE_FILE="pumpkin-release.jks"
KEY_ALIAS="pumpkin"

echo ""
echo "🎃  Pumpkin Android – Keystore Generator"
echo "========================================="
echo ""

command -v keytool >/dev/null 2>&1 || {
    echo "ERROR: keytool not found. Please install a JDK."
    exit 1
}

# Collect passwords
read -rsp "Keystore password (STORE_PASSWORD): " STORE_PASSWORD; echo
read -rsp "Key password (KEY_PASSWORD, press Enter to reuse store password): " KEY_PASSWORD; echo
KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

echo ""
echo "Developer information (press Enter to skip each field):"
read -rp "Name (e.g. Jane Smith): "   DN_CN
read -rp "Organization (e.g. PumpkinMC): " DN_O
read -rp "Country code (e.g. US): "   DN_C

DN_CN="${DN_CN:-Unknown}"
DN_O="${DN_O:-Unknown}"
DN_C="${DN_C:-US}"

echo ""
echo "Generating keystore..."
keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$STORE_PASSWORD" \
    -keypass  "$KEY_PASSWORD" \
    -dname "CN=${DN_CN}, O=${DN_O}, C=${DN_C}" \
    2>/dev/null

echo "Generated: $KEYSTORE_FILE"

KEYSTORE_BASE64=$(base64 -w 0 "$KEYSTORE_FILE")

echo ""
echo "================================================================"
echo "Add the following to your repository:"
echo "  Settings → Secrets and variables → Actions → New repository secret"
echo "================================================================"
echo ""
echo "Name:  KEYSTORE_BASE64"
echo "Value: $KEYSTORE_BASE64"
echo ""
echo "Name:  KEY_ALIAS"
echo "Value: $KEY_ALIAS"
echo ""
echo "Name:  KEY_PASSWORD"
echo "Value: $KEY_PASSWORD"
echo ""
echo "Name:  STORE_PASSWORD"
echo "Value: $STORE_PASSWORD"
echo ""
echo "================================================================"
echo "IMPORTANT: Keep $KEYSTORE_FILE in a safe place."
echo "Losing it means you cannot update the app on the Play Store."
echo "Make sure *.jks is listed in .gitignore."
echo "================================================================"

# Add *.jks to .gitignore if not already present
GITIGNORE="../.gitignore"
if [ -f "$GITIGNORE" ] && ! grep -q "\.jks" "$GITIGNORE"; then
    printf "\n# Android signing keystores\n*.jks\n*.keystore\n" >> "$GITIGNORE"
    echo "Added *.jks to .gitignore"
fi
