// diffusion_stub.cpp
//
// Minimal stub compiled when MNN is not available.
// Provides valid JNI method signatures that return 0 / nullptr
// so the Kotlin side can detect unavailability gracefully
// (nativeInit returns 0 → "model loading failed" instead of UnsatisfiedLinkError crash).

#include <jni.h>
#include <android/log.h>

#define STUB_TAG "ApkClawDiffusionStub"
#define STUB_LOGW(...) __android_log_print(ANDROID_LOG_WARN, STUB_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_apk_claw_android_local_diffusion_DiffusionEngine_nativeInit(
        JNIEnv *env,
        jobject /* thiz */,
        jstring /* resourcePath */,
        jint /* memoryMode */,
        jint /* backendType */) {

    STUB_LOGW("nativeInit called but MNN is not built — returning 0 (not available)");
    return 0;
}

JNIEXPORT jobject JNICALL
Java_com_apk_claw_android_local_diffusion_DiffusionEngine_nativeGenerate(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong /* instanceId */,
        jstring /* prompt */,
        jstring /* outputPath */,
        jint /* iterNum */,
        jint /* randomSeed */,
        jobject /* progressListener */) {

    return nullptr;
}

JNIEXPORT void JNICALL
Java_com_apk_claw_android_local_diffusion_DiffusionEngine_nativeRelease(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong /* instanceId */) {
    // no-op
}

} // extern "C"