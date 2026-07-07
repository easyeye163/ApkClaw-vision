// diffusion_stub.cpp
//
// Minimal stub compiled when MNN is not available.
// Produces a valid libapkclaw_diffusion.so so that
// System.loadLibrary("apkclaw_diffusion") succeeds, but the
// actual JNI methods are missing — DiffusionEngine catches
// UnsatisfiedLinkError and reports NativeNotAvailable.
//

// This file intentionally left empty.
// The real JNI methods live in diffusion_jni.cpp which requires MNN headers.