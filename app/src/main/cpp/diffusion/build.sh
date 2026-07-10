#!/bin/bash
# Build libapkclaw_diffusion.so
# Links diffusion engine against prebuilt MNN shared libraries

set -e

export PATH="/home/z/.local/bin:$PATH"

NDK=/home/z/android-sdk/ndk/27.2.12479018
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/linux-x86_64
API=21

CC=$TOOLCHAIN/bin/aarch64-linux-android$API-clang
CXX=$TOOLCHAIN/bin/aarch64-linux-android$API-clang++

# Source directories
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
DIFF_DIR=$SCRIPT_DIR
MNN_INC=$DIFF_DIR/mnn_include
JNI_DIR=$DIFF_DIR

# Output
OUT_DIR=/home/z/my-project/ApkClaw-vision/app/src/main/jniLibs/arm64-v8a
mkdir -p $OUT_DIR

OUT_LIB=$OUT_DIR/libapkclaw_diffusion.so

echo "=== Building libapkclaw_diffusion.so ==="
echo "NDK: $NDK"
echo "MNN_INC: $MNN_INC"
echo "Output: $OUT_LIB"

# Prebuilt MNN lib directory (for linking)
PREBUILT_LIB=$OUT_DIR

# Compile flags
CXXFLAGS="-std=c++17 -O2 -fPIC -fvisibility=default"
CXXFLAGS="$CXXFLAGS -DANDROID -DMNN_BUILD_FOR_ANDROID -DMNN_DIFFUSION_WITH_LLM_TOKENIZER"
CXXFLAGS="$CXXFLAGS -DMNN_USE_LOGCAT"
CXXFLAGS="$CXXFLAGS -I$DIFF_DIR/include"
CXXFLAGS="$CXXFLAGS -I$DIFF_DIR"
CXXFLAGS="$CXXFLAGS -I$MNN_INC"
CXXFLAGS="$CXXFLAGS -I$MNN_INC/MNN"
CXXFLAGS="$CXXFLAGS -I$MNN_INC/core"
CXXFLAGS="$CXXFLAGS -I$MNN_INC/cv"
CXXFLAGS="$CXXFLAGS -I$MNN_INC/llm_tokenizer"

# We need the MNN expr headers that depend on schema
CXXFLAGS="$CXXFLAGS -I$MNN_INC/schema"

LDFLAGS="-shared -L$PREBUILT_LIB"
LDFLAGS="$LDFLAGS -lMNN -lMNN_Express -lMNNOpenCV -lllm"
LDFLAGS="$LDFLAGS -llog -landroid -ldl"
LDFLAGS="$LDFLAGS -Wl,-z,max-page-size=16384"

# Source files
SOURCES="
  $DIFF_DIR/diffusion.cpp
  $DIFF_DIR/stable_diffusion.cpp
  $DIFF_DIR/scheduler.cpp
  $DIFF_DIR/tokenizer.cpp
  $DIFF_DIR/diffusion_jni.cpp
"

echo "Compiling..."
OBJS=""
for SRC in $SOURCES; do
  OBJ=$(basename "$SRC" .cpp).o
  echo "  $SRC"
  $CXX $CXXFLAGS -c "$SRC" -o "$DIFF_DIR/$OBJ"
  OBJS="$OBJS $DIFF_DIR/$OBJ"
done

echo "Linking..."
$CXX $OBJS $LDFLAGS -o "$OUT_LIB"

echo "=== SUCCESS ==="
ls -lh "$OUT_LIB"

# Cleanup objects
rm -f $DIFF_DIR/*.o