#!/bin/bash

echo "========================================"
echo "  Bayport Veterinary Clinic - macOS Builder"
echo "========================================"
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js is not installed or not in PATH"
    echo "Please install Node.js from https://nodejs.org/"
    exit 1
fi

echo "[1/1] Building macOS DMG..."
echo ""
echo "This may take a few minutes..."
echo ""

npm run build:mac

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "  BUILD SUCCESSFUL!"
    echo "========================================"
    echo ""
    echo "Your DMG files are located at:"
    echo "  dist/Bayport-Veterinary-Clinic-0.1.0-x64.dmg (Intel Macs)"
    echo "  dist/Bayport-Veterinary-Clinic-0.1.0-arm64.dmg (Apple Silicon Macs)"
    echo ""
    echo "You can now distribute these files to users."
    echo "They can double-click the DMG to install - no terminal needed!"
    echo ""
else
    echo ""
    echo "========================================"
    echo "  BUILD FAILED"
    echo "========================================"
    echo ""
    echo "Please check the error messages above."
    echo ""
    exit 1
fi

