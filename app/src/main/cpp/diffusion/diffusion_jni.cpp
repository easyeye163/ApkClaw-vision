//
// diffusion_jni.cpp
// JNI bridge for MNN-Diffusion in ApkClaw
//

#include <jni.h>
#include <android/log.h>
#include <string>
#include <chrono>
#include "diffusion_session.h"

#define JNI_TAG "ApkClawDiffusionJNI"
#define JNI_LOGI(...) __android_log_print(ANDROID_LOG_INFO, JNI_TAG, __VA_ARGS__)
#define JNI_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, JNI_TAG, __VA_ARGS__)

static jint progressMethodId = 0;

extern "C" {

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

    try {
        auto *session = new aclaw::DiffusionSession(pathStr, memoryMode, backendType);
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
    jobject successValue = env->GetStaticObjectField(
            env->FindClass("java/lang/Boolean"),
            env->GetStaticMethodID(env->FindClass("java/lang/Boolean"), "VALUE",
                                   "Ljava/lang/Boolean;"));
    // Actually use Boolean.valueOf
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