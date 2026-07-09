//
// diffusion_jni.cpp
// JNI bridge for MNN-Diffusion in ApkClaw
//

#include <jni.h>
#include <android/log.h>
#include <string>
#include <chrono>
#include <dlfcn.h>
#include "diffusion_session.h"

// Minimal OpenCL types for dlsym-based check (avoid depending on CL headers at compile time)
typedef unsigned int cl_uint;
typedef int cl_int;
#define CL_SUCCESS 0

#define JNI_TAG "ApkClawDiffusionJNI"
#define JNI_LOGI(...) __android_log_print(ANDROID_LOG_INFO, JNI_TAG, __VA_ARGS__)
#define JNI_LOGW(...) __android_log_print(ANDROID_LOG_WARN, JNI_TAG, __VA_ARGS__)
#define JNI_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, JNI_TAG, __VA_ARGS__)

static jint progressMethodId = 0;

extern "C" {

// Check if OpenCL is available on this device.
// Returns JNI_TRUE if at least one OpenCL platform/device is found,
// JNI_FALSE otherwise. Safe to call on any device.
JNIEXPORT jboolean JNICALL
Java_com_apk_claw_android_local_diffusion_DiffusionEngine_nativeCheckOpenCL(
        JNIEnv * /* env */,
        jobject /* thiz */) {

    // MNN's OpenCL backend will attempt clGetPlatformIDs internally.
    // We mimic that check here to avoid loading the full Diffusion model.
    // If libOpenCL.so is missing (common on older/low-end devices),
    // dlopen will fail and we return false.
    void *libCL = dlopen("libOpenCL.so", RTLD_NOW | RTLD_LOCAL);
    if (!libCL) {
        JNI_LOGI("nativeCheckOpenCL: libOpenCL.so not found (%s)", dlerror());
        return JNI_FALSE;
    }

    typedef cl_int (*clGetPlatformIDs_fn)(cl_uint, void *, cl_uint *);
    auto fn = (clGetPlatformIDs_fn)dlsym(libCL, "clGetPlatformIDs");
    if (!fn) {
        dlclose(libCL);
        JNI_LOGI("nativeCheckOpenCL: clGetPlatformIDs not found");
        return JNI_FALSE;
    }

    cl_uint numPlatforms = 0;
    cl_int err = fn(0, nullptr, &numPlatforms);
    dlclose(libCL);

    if (err != CL_SUCCESS || numPlatforms == 0) {
        JNI_LOGI("nativeCheckOpenCL: no OpenCL platforms found (err=%d)", err);
        return JNI_FALSE;
    }

    JNI_LOGI("nativeCheckOpenCL: %u platform(s) found", numPlatforms);
    return JNI_TRUE;
}

// Initialize a native DiffusionSession.
// Returns a pointer cast to jlong (0 on failure).
JNIEXPORT jlong JNICALL
Java_com_apk_claw_android_local_diffusion_DiffusionEngine_nativeInit(
        JNIEnv *env,
        jobject /* thiz */,
        jstring resourcePath,
        jint memoryMode,
        jint backendType) {

    const char *path = env->GetStringUTFChars(resourcePath, nullptr);
    std::string pathStr(path ? path : "");
    env->ReleaseStringUTFChars(resourcePath, path);

    if (pathStr.empty()) {
        JNI_LOGE("nativeInit: empty resource path");
        return 0;
    }

    // MNN_FORWARD_OPENCL = 3, MNN_FORWARD_CPU = 0
    const int MNN_FORWARD_OPENCL = 3;
    const int MNN_FORWARD_CPU = 0;

    // If OpenCL was requested, try it first; on failure, fall back to CPU automatically.
    // This handles low-end GPUs (Adreno 305/306/505 on Snapdragon 4) where OpenCL
    // may be present but too limited for MNN Diffusion workloads.
    int effectiveBackend = backendType;
    bool triedOpenCL = false;

    if (backendType == MNN_FORWARD_OPENCL) {
        triedOpenCL = true;
    }

    try {
        auto *session = new aclaw::DiffusionSession(pathStr, memoryMode, effectiveBackend);
        if (!session->isLoaded()) {
            if (triedOpenCL && effectiveBackend == MNN_FORWARD_OPENCL) {
                // OpenCL failed — retry with CPU
                JNI_LOGW("nativeInit: OpenCL load failed, retrying with CPU backend");
                delete session;
                effectiveBackend = MNN_FORWARD_CPU;
                session = new aclaw::DiffusionSession(pathStr, memoryMode, effectiveBackend);
                if (!session->isLoaded()) {
                    JNI_LOGE("nativeInit: CPU fallback also failed");
                    delete session;
                    return 0;
                }
                JNI_LOGI("nativeInit: CPU fallback succeeded");
            } else {
                JNI_LOGE("nativeInit: load() failed");
                delete session;
                return 0;
            }
        }
        return reinterpret_cast<jlong>(session);
    } catch (const std::exception &e) {
        JNI_LOGE("nativeInit exception: %s", e.what());
        return 0;
    }
}

// Run diffusion and save image to outputPath.
// Returns a HashMap with "success" (boolean) and "totalTimeMs" (long).
JNIEXPORT jobject JNICALL
Java_com_apk_claw_android_local_diffusion_DiffusionEngine_nativeGenerate(
        JNIEnv *env,
        jobject /* thiz */,
        jlong instanceId,
        jstring prompt,
        jstring outputPath,
        jint iterNum,
        jint randomSeed,
        jobject progressListener) {

    auto *session = reinterpret_cast<aclaw::DiffusionSession *>(instanceId);
    if (!session) {
        JNI_LOGE("nativeGenerate: null session pointer");
        return nullptr;
    }

    // Get progress callback method
    jclass listenerClass = env->GetObjectClass(progressListener);
    jmethodID onProgressMethod = env->GetMethodID(listenerClass, "onProgress", "(I)V");
    if (!onProgressMethod) {
        JNI_LOGE("onProgress(I)V method not found on listener");
    }

    const char *promptStr = env->GetStringUTFChars(prompt, nullptr);
    const char *outputStr = env->GetStringUTFChars(outputPath, nullptr);

    std::string promptCpp(promptStr ? promptStr : "");
    std::string outputCpp(outputStr ? outputStr : "");

    env->ReleaseStringUTFChars(prompt, promptStr);
    env->ReleaseStringUTFChars(outputPath, outputStr);

    auto startTime = std::chrono::high_resolution_clock::now();

    bool success = true;
    std::string errorMsg;

    try {
        session->run(promptCpp, outputCpp, iterNum, randomSeed,
                     [env, progressListener, onProgressMethod](int progress) {
                         if (progressListener && onProgressMethod) {
                             env->CallVoidMethod(progressListener, onProgressMethod, progress);
                         }
                     });
    } catch (const std::exception &e) {
        success = false;
        errorMsg = e.what();
        JNI_LOGE("nativeGenerate exception: %s", errorMsg.c_str());
    }

    auto endTime = std::chrono::high_resolution_clock::now();
    auto durationMs = std::chrono::duration_cast<std::chrono::milliseconds>(
            endTime - startTime).count();

    // Build result HashMap
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID initMethod = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put",
                                           "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jobject hashMap = env->NewObject(hashMapClass, initMethod);

    // success
    jstring successKey = env->NewStringUTF("success");
    jclass boolClass = env->FindClass("java/lang/Boolean");
    jmethodID valueOfMethod = env->GetStaticMethodID(boolClass, "valueOf", "(Z)Ljava/lang/Boolean;");
    jobject successObj = env->CallStaticObjectMethod(boolClass, valueOfMethod, success);
    env->CallObjectMethod(hashMap, putMethod, successKey, successObj);
    env->DeleteLocalRef(successKey);
    env->DeleteLocalRef(successObj);

    // totalTimeMs
    jstring timeKey = env->NewStringUTF("totalTimeMs");
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");
    jobject timeValue = env->NewObject(longClass, longInit, (jlong) durationMs);
    env->CallObjectMethod(hashMap, putMethod, timeKey, timeValue);
    env->DeleteLocalRef(timeKey);
    env->DeleteLocalRef(timeValue);

    // error (only if failed)
    if (!success && !errorMsg.empty()) {
        jstring errorKey = env->NewStringUTF("error");
        jstring errorValue = env->NewStringUTF(errorMsg.c_str());
        env->CallObjectMethod(hashMap, putMethod, errorKey, errorValue);
        env->DeleteLocalRef(errorKey);
        env->DeleteLocalRef(errorValue);
    }

    return hashMap;
}

// Release native DiffusionSession.
JNIEXPORT void JNICALL
Java_com_apk_claw_android_local_diffusion_DiffusionEngine_nativeRelease(
        JNIEnv *env,
        jobject /* thiz */,
        jlong instanceId) {

    auto *session = reinterpret_cast<aclaw::DiffusionSession *>(instanceId);
    if (session) {
        delete session;
        JNI_LOGI("Native diffusion session released");
    }
}

} // extern "C"