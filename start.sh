#!/bin/bash
# pi-mobile launcher — start pi-web + PWA proxy
set -e

cd "$(dirname "$0")"

echo "📱 Pi Mobile Launcher"
echo "====================="
echo ""

# Check if pi-web is available
if command -v pi-web &> /dev/null; then
    echo "✓ pi-web found on PATH"
elif npx --yes @agegr/pi-web@latest --version &> /dev/null; then
    echo "✓ pi-web available via npx"
fi

echo ""
echo "Starting server..."
node server/index.js
