# MNN 自编译构建指南

## 骁龙 4 芯片说明

骁龙 4 系列芯片（如 Snapdragon 425/430/435/439 等）的 GPU (Adreno 305/306/505) **OpenCL 支持有限**，Prepare 阶段可能失败。建议：
- 优先使用 **CPU 后端**（`MNN_FORWARD_CPU`）
- 如需 OpenCL，需在运行时检测设备支持情况，失败后自动回退 CPU

> ⚠️ 自编译 MNN 启用 `MNN_ARM82=true` 时，生成的 .so 包含 ARMv8.2 指令（FP16/dotprod），
> **不兼容** 骁龙 4 的 Cortex-A53 (ARMv8.0)。如需兼容骁龙 4，请使用官方预编译 .so 或设置 `MNN_ARM82=OFF`。

---

## 环境准备

- Android Studio
- NDK（与 app/build.gradle.kts 一致，当前: `27.2.12479018`）

```bash
export ANDROID_NDK=${YOUR_NDK_ROOT}
# 例如:
# export ANDROID_NDK=/home/z/android-sdk/ndk/27.2.12479018
```

---

## 1. Clone MNN

```bash
git clone https://github.com/alibaba/MNN.git
cd MNN
```

---

## 2. 编译 MNN 库 (arm64-v8a)

```bash
cd project/android
mkdir build_64
cd build_64
../build_64.sh "-DMNN_LOW_MEMORY=true -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_BUILD_LLM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true -DMNN_ARM82=true -DMNN_USE_LOGCAT=true -DMNN_OPENCL=true -DLLM_SUPPORT_VISION=true -DMNN_BUILD_OPENCV=true -DMNN_IMGCODECS=true -DLLM_SUPPORT_AUDIO=true -DMNN_BUILD_AUDIO=true -DMNN_BUILD_DIFFUSION=ON -DMNN_SEP_BUILD=OFF -DCMAKE_SHARED_LINKER_FLAGS='-Wl,-z,max-page-size=16384' -DCMAKE_INSTALL_PREFIX=."
make install
```

**生成的 .so 文件**（在 `build_64/` 目录下）：

| 文件 | 用途 |
|------|------|
| `libMNN.so` | MNN 核心推理引擎 |
| `libMNN_CL.so` | OpenCL 后端 |
| `libMNN_Express.so` | Express 高级 API |
| `libMNNOpenCV.so` | 图像处理 |
| `libllm.so` | LLM / Diffusion 引擎 |

---

## 3. 复制 .so 到 ApkClaw 项目

```bash
# 将编译产物复制到 jniLibs
MNN_BUILD=MNN/project/android/build_64
APKCLAW_JNI=ApkClaw-vision/app/src/main/jniLibs/arm64-v8a

cp $MNN_BUILD/libMNN.so          $APKCLAW_JNI/
cp $MNN_BUILD/libMNN_CL.so       $APKCLAW_JNI/
cp $MNN_BUILD/libMNN_Express.so  $APKCLAW_JNI/
cp $MNN_BUILD/libMNNOpenCV.so    $APKCLAW_JNI/
cp $MNN_BUILD/libllm.so         $APKCLAW_JNI/
```

---

## 4. 编译 libapkclaw_diffusion.so (JNI 桥接)

```bash
cd ApkClaw-vision/app/src/main/cpp/diffusion
# 确保 build.sh 中的 MNN_SRC 指向你的 MNN 源码路径
bash build.sh
```

---

## 5. 编译并安装 APK

### 方式 A：Android Studio
直接在 Android Studio 中 Build & Run

### 方式 B：命令行

```bash
cd ApkClaw-vision
JAVA_HOME=${YOUR_JDK17_HOME} \
  ANDROID_HOME=${YOUR_ANDROID_SDK} \
  ./gradlew assembleRelease --no-daemon
```

### 方式 C：MNN 官方 Demo App（参考）

```bash
cd MNN/apps/Android/MnnLlmChat
./installDebug.sh
```

---

## CMake 参数说明

| 参数 | 值 | 说明 |
|------|----|------|
| `MNN_LOW_MEMORY` | `true` | 低内存模式，减少运行时内存占用 |
| `MNN_CPU_WEIGHT_DEQUANT_GEMM` | `true` | CPU 权重反量化 GEMM 优化 |
| `MNN_BUILD_LLM` | `true` | 编译 LLM 支持 |
| `MNN_SUPPORT_TRANSFORMER_FUSE` | `true` | Transformer 算子融合 |
| `MNN_ARM82` | `true` | 启用 ARMv8.2 指令（FP16/dotprod），**不兼容骁龙4** |
| `MNN_USE_LOGCAT` | `true` | 使用 Android logcat 输出日志 |
| `MNN_OPENCL` | `true` | 启用 OpenCL GPU 后端 |
| `MNN_BUILD_OPENCV` | `true` | 编译 MNN OpenCV 模块 |
| `MNN_IMGCODECS` | `true` | 编译图像编解码支持 |
| `MNN_BUILD_DIFFUSION` | `ON` | 编译 Diffusion 文生图引擎 |
| `MNN_SEP_BUILD` | `OFF` | 不分离构建，所有模块编译到一起 |
| `LLM_SUPPORT_VISION` | `true` | LLM 支持视觉输入 |
| `LLM_SUPPORT_AUDIO` | `true` | LLM 支持音频输入 |
| `MNN_BUILD_AUDIO` | `true` | 编译音频模块 |

---

## 骁龙 4 兼容方案

如果目标设备是骁龙 4（Cortex-A53, ARMv8.0），有两种方案：

### 方案 A：使用官方预编译 .so（推荐）
直接使用 MNN Release 页面的预编译包，已适配 ARMv8.0：
```bash
# 下载地址
https://github.com/alibaba/MNN/releases/download/3.6.0/mnn_3.6.0_android_armv7_armv8_cpu_opencl_vulkan.zip
```

### 方案 B：自编译时关闭 ARM8.2
将 `MNN_ARM82=true` 改为 `MNN_ARM82=OFF`：
```bash
../build_64.sh "-DMNN_ARM82=OFF ... (其他参数不变)"
```