# CMakeLists.txt for ApkClaw Diffusion JNI bridge
#
# BUILD PREREQUISITE (for diffusion support):
#   1. Run scripts/build_mnn.sh to build libMNN.so and copy MNN headers
#   2. libMNN.so must exist at: app/src/main/jniLibs/arm64-v8a/libMNN.so
#   3. MNN headers must exist at: app/src/main/cpp/mnn_include/
#
# If MNN is not built yet, the build will succeed but DiffusionEngine will
# report NativeNotAvailable at runtime.
#

cmake_minimum_required(VERSION 3.22.1)
project("apkclaw_diffusion")

set(MNN_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/mnn_include")
set(MNN_JNI_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

# Check if MNN is available (headers + .so)
if(EXISTS "${MNN_INCLUDE_DIR}/MNN/Interpreter.hpp"
   AND EXISTS "${MNN_JNI_LIB_DIR}/libMNN.so")

    message(STATUS "MNN found — building diffusion JNI bridge")

    # Import prebuilt libMNN.so
    add_library(MNN SHARED IMPORTED)
    set_target_properties(MNN PROPERTIES
        IMPORTED_LOCATION "${MNN_JNI_LIB_DIR}/libMNN.so"
    )

    # Build the diffusion JNI bridge as a shared library
    add_library(${CMAKE_PROJECT_NAME} SHARED
        diffusion/diffusion_session.cpp
        diffusion/diffusion_jni.cpp
    )

    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${MNN_INCLUDE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}
    )

    target_compile_features(${CMAKE_PROJECT_NAME} PRIVATE cxx_std_17)

    target_link_libraries(${CMAKE_PROJECT_NAME}
        MNN
        android
        log
    )

    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

else()
    message(WARNING "MNN headers/lib not found. "
            "Diffusion JNI bridge will NOT be built. "
            "Run scripts/build_mnn.sh to enable MNN-Diffusion support.")

    # Create a minimal stub .so so System.loadLibrary("apkclaw_diffusion")
    # doesn't crash at class-load time. The Kotlin side catches
    # UnsatisfiedLinkError and reports NativeNotAvailable.
    add_library(${CMAKE_PROJECT_NAME} SHARED
        diffusion/diffusion_stub.cpp
    )
    target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
    )
    target_link_libraries(${CMAKE_PROJECT_NAME} android log)
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
endif()