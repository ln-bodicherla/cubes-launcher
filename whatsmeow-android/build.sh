#!/bin/bash
set -e

# Find NDK path
if [ -z "$ANDROID_HOME" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
fi

NDK_DIR=$(ls -d "$ANDROID_HOME/ndk/"* 2>/dev/null | sort -V | tail -1)
if [ -z "$NDK_DIR" ]; then
    echo "ERROR: No Android NDK found in $ANDROID_HOME/ndk/"
    echo "Install NDK via Android Studio: SDK Manager > SDK Tools > NDK (Side by side)"
    exit 1
fi

export ANDROID_NDK_HOME="$NDK_DIR"
echo "Using NDK: $ANDROID_NDK_HOME"

# Use Go module proxy to resolve go.mau.fi packages
export GOPROXY=https://proxy.golang.org,direct
export GONOSUMCHECK='go.mau.fi/*'
export GONOSUMDB='go.mau.fi/*'

# Ensure gomobile is installed
if ! command -v gomobile &> /dev/null; then
    echo "Installing gomobile..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    gomobile init
fi

# Download dependencies
echo "Downloading Go dependencies..."
go mod tidy

# Build .aar for Android
echo "Building whatsmeow.aar..."
gomobile bind -target=android/arm64,android/arm -androidapi 26 -o ../messages/libs/whatsmeow.aar .

echo "Done! Output: ../messages/libs/whatsmeow.aar"
