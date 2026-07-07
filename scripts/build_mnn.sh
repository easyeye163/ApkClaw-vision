#!/bin/bash
# ==============================================================================
# build_mnn.sh — Build libMNN.so for Android arm64-v8a with Diffusion support
#
# Prerequisites:
#   - Android NDK (r27+) installed
#   - CMake 3.22+
#   - Git
#   - OpenCL headers (usually included with NDK)
#
# Usage:
#   cd ApkClaw-vision
#   bash scripts/build_mnn.sh
#
# Output:
#   - app/src/main/jniLibs/arm64-v8a/libMNN.so
#   - app/src/main/cpp/mnn_include/  (headers)
#
# After running, build the APK normally with Gradle.
# ==============================================================================

set -e

# ── Configuration ─────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
APP_DIR="$PROJECT_DIR/app"
JNI_DIR="$APP_DIR/src/main/jniLibs/arm64-v8a"
MNN_INCLUDE_DIR="$APP_DIR/src/main/cpp/mnn_include"
MNN_BUILD_DIR="/tmp/mnn_android_build"
MNN_SOURCE_DIR="/tmp/MNN_build"

NDK_PATH="${ANDROID_NDK_HOME:-/home/z/android-sdk/ndk/27.2.12479018}"
API_LEVEL=28  # minSdk

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ── Check Prerequisites ────────────────────────────────────────────────────
check_prerequisites() {
    log_info "Checking prerequisites..."

    if [ ! -d "$NDK_PATH" ]; then
        log_error "NDK not found at $NDK_PATH"
        log_error "Set ANDROID_NDK_HOME or update NDK_PATH in this script"
        exit 1
    fi
    log_info "NDK found: $NDK_PATH"

    if ! command -v cmake &> /dev/null; then
        log_error "cmake not found. Install it first."
        exit 1
    fi
    log_info "cmake found: $(cmake --version | head -1)"

    if ! command -v git &> /dev/null; then
        log_error "git not found. Install it first."
        exit 1
    fi
}

# ── Clone MNN ───────────────────────────────────────────────────────────────
clone_mnn() {
    log_info "Cloning MNN repository (shallow)..."
    
    if [ -d "$MNN_SOURCE_DIR" ]; then
        log_info "MNN source already exists at $MNN_SOURCE_DIR, updating..."
        cd "$MNN_SOURCE_DIR" && git pull --ff-only || true
    else
        git clone --depth 1 https://github.com/alibaba/MNN.git "$MNN_SOURCE_DIR"
    fi

    log_info "MNN source ready at $MNN_SOURCE_DIR"
}

# ── Build libMNN.so ─────────────────────────────────────────────────────────
build_mnn() {
    log_info "Building libMNN.so for Android arm64-v8a with Diffusion support..."

    mkdir -p "$MNN_BUILD_DIR"
    cd "$MNN_BUILD_DIR"

    # MNN's Android build script
    BUILD_SCRIPT="$MNN_SOURCE_DIR/project/android/build_64.sh"

    if [ ! -f "$BUILD_SCRIPT" ]; then
        log_error "Build script not found: $BUILD_SCRIPT"
        exit 1
    fi

    # Set environment for the build script
    export ANDROID_NDK="$NDK_PATH"

    # Run MNN's Android build with Diffusion flags
    bash "$BUILD_SCRIPT" \
        "-DMNN_BUILD_DIFFUSION=ON" \
        "-DMNN_LOW_MEMORY=ON" \
        "-DMNN_BUILD_OPENCV=ON" \
        "-DMNN_IMGCODECS=ON" \
        "-DMNN_OPENCL=ON" \
        "-DMNN_SEP_BUILD=OFF" \
        "-DMNN_SUPPORT_TRANSFORMER_FUSE=ON" \
        "-DANDROID_ABI=arm64-v8a" \
        "-DANDROID_NATIVE_API_LEVEL=$API_LEVEL"

    # Check if build succeeded
    local LIB_PATH="$MNN_BUILD_DIR/lib/libMNN.so"
    if [ ! -f "$LIB_PATH" ]; then
        log_error "Build failed: libMNN.so not found at $LIB_PATH"
        exit 1
    fi

    log_info "libMNN.so built successfully: $LIB_PATH"
    ls -lh "$LIB_PATH"
}

# ── Install to Project ──────────────────────────────────────────────────────
install_to_project() {
    log_info "Installing libMNN.so to project..."

    # Copy .so to jniLibs
    mkdir -p "$JNI_DIR"
    cp "$MNN_BUILD_DIR/lib/libMNN.so" "$JNI_DIR/libMNN.so"
    log_info "Copied libMNN.so -> $JNI_DIR/"

    # Copy headers
    log_info "Copying MNN headers to $MNN_INCLUDE_DIR..."
    rm -rf "$MNN_INCLUDE_DIR"
    mkdir -p "$MNN_INCLUDE_DIR"

    # Core MNN headers
    if [ -d "$MNN_BUILD_DIR/include" ]; then
        cp -r "$MNN_BUILD_DIR/include/"* "$MNN_INCLUDE_DIR/"
    fi
    # Also copy from source include (some headers may only be there)
    if [ -d "$MNN_SOURCE_DIR/include" ]; then
        cp -rn "$MNN_SOURCE_DIR/include/"* "$MNN_INCLUDE_DIR/" 2>/dev/null || true
    fi

    # Diffusion-specific headers
    if [ -d "$MNN_SOURCE_DIR/transformers/diffusion/engine/include" ]; then
        cp -r "$MNN_SOURCE_DIR/transformers/diffusion/engine/include/"* \
              "$MNN_INCLUDE_DIR/" 2>/dev/null || true
    fi

    log_info "Headers installed. Contents:"
    find "$MNN_INCLUDE_DIR" -name "*.hpp" -o -name "*.h" | head -20
    local HEADER_COUNT=$(find "$MNN_INCLUDE_DIR" -name "*.hpp" -o -name "*.h" | wc -l)
    log_info "Total header files: $HEADER_COUNT"
}

# ── Print Model Download Instructions ───────────────────────────────────────
print_model_instructions() {
    echo ""
    log_info "============================================================"
    log_info "Build complete! Next steps:"
    log_info ""
    log_info "1. Download MNN-Diffusion model from ModelScope:"
    log_info "   https://modelscope.cn/models/MNN/stable-diffusion-v1-5-mnn"
    log_info ""
    log_info "   Or the OpenCL-optimized version:"
    log_info "   https://modelscope.cn/models/MNN/stable-diffusion-v1-5-mnn-opencl"
    log_info ""
    log_info "   Use 'pip install modelscope' then:"
    log_info "   from modelscope import snapshot_download"
    log_info "   snapshot_download('MNN/stable-diffusion-v1-5-mnn-opencl',"
    log_info "                       local_dir='./sd1.5-mnn')"
    log_info ""
    log_info "2. Push model to Android device:"
    log_info "   adb push sd1.5-mnn/ /sdcard/ApkClaw/sd1.5-mnn/"
    log_info ""
    log_info "3. In the app chat, click the palette icon and set"
    log_info "   model path to: /sdcard/ApkClaw/sd1.5-mnn"
    log_info "============================================================"
    echo ""
}

# ── Main ────────────────────────────────────────────────────────────────────
main() {
    echo ""
    log_info "╔══════════════════════════════════════════════════╗"
    log_info "║  MNN-Diffusion Android Build Script              ║"
    log_info "║  Target: arm64-v8a with OpenCL + Diffusion       ║"
    log_info "╚══════════════════════════════════════════════════╝"
    echo ""

    check_prerequisites
    clone_mnn
    build_mnn
    install_to_project
    print_model_instructions

    log_info "Done! You can now build the APK with Gradle."
}

main "$@"