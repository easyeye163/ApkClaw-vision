//
// diffusion_session.cpp
// MNN-Diffusion implementation for ApkClaw
//
// BUILD REQUIREMENT: This file requires MNN headers to be present at
//   app/src/main/cpp/mnn_include/
// Run scripts/build_mnn.sh to build libMNN.so and copy headers.
//

#include "diffusion_session.h"
#include <android/log.h>

// MNN headers (available after build_mnn.sh)
#include "diffusion/diffusion.hpp"

#define DIFF_LOG_TAG "ApkClawDiffusion"
#define DIFF_LOGI(...) __android_log_print(ANDROID_LOG_INFO, DIFF_LOG_TAG, __VA_ARGS__)
#define DIFF_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DIFF_LOG_TAG, __VA_ARGS__)

using namespace MNN::DIFFUSION;

// Wrapper to hold MNN Diffusion pointer without exposing MNN headers in .h
class MNN_DIFFUSION_Diffusion {
public:
    std::unique_ptr<Diffusion> ptr;
};

namespace aclaw {

DiffusionSession::DiffusionSession(std::string resourcePath, int memoryMode, int backendType)
    : resourcePath_(std::move(resourcePath)),
      memoryMode_(memoryMode),
      backendType_(backendType) {

    auto forwardType = static_cast<MNN::MNNForwardType>(backendType);

    DIFF_LOGI("Creating Diffusion: path=%s memoryMode=%d backend=%d",
              resourcePath_.c_str(), memoryMode_, backendType_);

    auto d = std::make_unique<MNN_DIFFUSION_Diffusion>();
    d->ptr.reset(Diffusion::createDiffusion(
        resourcePath_,
        DiffusionModelType::STABLE_DIFFUSION_1_5,
        forwardType,
        memoryMode_
    ));

    if (d->ptr) {
        DIFF_LOGI("Diffusion created, loading models...");
        d->ptr->load();
        loaded_ = true;
        DIFF_LOGI("Diffusion models loaded successfully");
        diffusion_ = std::move(d);
    } else {
        DIFF_LOGE("Failed to create Diffusion instance");
    }
}

DiffusionSession::~DiffusionSession() {
    if (diffusion_ && diffusion_->ptr) {
        diffusion_->ptr.reset();
        loaded_ = false;
        DIFF_LOGI("DiffusionSession destroyed");
    }
}

void DiffusionSession::run(const std::string& prompt,
                            const std::string& outputPath,
                            int iterNum,
                            int randomSeed,
                            const std::function<void(int)>& progressCallback) {
    if (!diffusion_ || !diffusion_->ptr) {
        DIFF_LOGE("Diffusion not initialized, cannot run");
        return;
    }

    if (!loaded_) {
        DIFF_LOGI("Reloading diffusion models...");
        diffusion_->ptr->load();
        loaded_ = true;
    }

    DIFF_LOGI("Starting generation: prompt='%s' output='%s' steps=%d seed=%d",
              prompt.c_str(), outputPath.c_str(), iterNum, randomSeed);

    diffusion_->ptr->run(prompt, "", iterNum, randomSeed, progressCallback);

    DIFF_LOGI("Generation complete, output: %s", outputPath.c_str());
}

} // namespace aclaw