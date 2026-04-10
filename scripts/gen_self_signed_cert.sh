#!/bin/bash
# Generate self-signed TLS certificate for local development
# Output: nginx/certs/fullchain.pem and nginx/certs/privkey.pem

set -e

CERT_DIR="$(dirname "$0")/../nginx/certs"
mkdir -p "$CERT_DIR"

openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 \
  -keyout "$CERT_DIR/privkey.pem" \
  -out "$CERT_DIR/fullchain.pem" \
  -subj "/CN=10.0.2.2/O=SimpleBank Local/C=ID" \
  -addext "subjectAltName=IP:10.0.2.2,IP:127.0.0.1,DNS:localhost"

echo ""
echo "Self-signed cert generated at:"
echo "  $CERT_DIR/fullchain.pem"
echo "  $CERT_DIR/privkey.pem"
echo ""
echo "Next: Install fullchain.pem as trusted CA on Android Emulator:"
echo "  1. Drag-and-drop fullchain.pem onto emulator window"
echo "  2. Settings -> Security -> Install from storage -> select the file"
