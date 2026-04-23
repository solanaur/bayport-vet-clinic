#!/bin/bash

echo "========================================"
echo "Bayport Desktop App - Quick Start"
echo "========================================"
echo ""

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "ERROR: Node.js is not installed or not in PATH"
    echo "Please install Node.js from https://nodejs.org/"
    exit 1
fi

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    echo "Please install Java 17+ from https://adoptium.net/"
    exit 1
fi

echo "[1/3] Installing Node.js dependencies..."
npm install
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to install dependencies"
    exit 1
fi

echo ""
echo "[2/3] Building backend JAR..."
npm run build-backend
if [ $? -ne 0 ]; then
    echo "ERROR: Failed to build backend"
    exit 1
fi

echo ""
echo "[3/3] Starting desktop application..."
echo ""
echo "The app will open in a new window."
echo "Press Ctrl+C to stop the application."
echo ""

npm run dev

