#!/bin/bash
# DevxyzDNS build environment setup: JDK 17 + Android SDK (cmdline-tools, platform 34, build-tools 34)
set -e
export DEBIAN_FRONTEND=noninteractive
echo "=== Installing JDK 17 ==="
sudo apt-get update -qq
sudo apt-get install -y -qq openjdk-17-jdk-headless unzip
java -version 2>&1 | head -2

echo "=== Downloading Android cmdline-tools ==="
mkdir -p ~/android-sdk/cmdline-tools
cd /tmp
if [ ! -f cmdtools.zip ]; then
  curl -L -o cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
fi
rm -rf ~/android-sdk/cmdline-tools/latest
unzip -q -o cmdtools.zip -d ~/android-sdk/cmdline-tools
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

echo "=== Installing SDK packages ==="
export ANDROID_HOME=~/android-sdk ANDROID_SDK_ROOT=~/android-sdk
export PATH=~/android-sdk/cmdline-tools/latest/bin:$PATH
yes | sdkmanager --licenses > /dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "=== DONE ==="
ls ~/android-sdk
