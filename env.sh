#!/bin/bash
# ApkClaw-vision 编译环境配置
# 使用方法: source /home/z/my-project/ApkClaw-vision/env.sh

export JAVA_HOME=/home/z/jdk-17.0.19+10
export ANDROID_HOME=/home/z/android-sdk
export ANDROID_NDK_HOME=/home/z/android-sdk/ndk/27.2.12479018
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.1.0:$PATH"

echo "✅ ApkClaw 编译环境已加载"
echo "  JAVA_HOME:  $JAVA_HOME"
echo "  ANDROID_HOME: $ANDROID_HOME"
echo "  NDK:         $ANDROID_NDK_HOME"
echo "  Java:        $(java -version 2>&1 | head -1)"
echo ""
echo "编译命令:"
echo "  cd /home/z/my-project/ApkClaw-vision && ./gradlew assembleDebug"