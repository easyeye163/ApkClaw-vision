//
// diffusion_session.cpp
// MNN-Diffusion implementation for ApkClaw
//

#include "diffusion_session.h"
#include <android/log.h>

#define DIFF_LOG_TAG "ApkClawDiffusion"
#define DIFF_LOGI(...) __android_log_print(ANDROID_LOG_INFO, DIFF_LOG_TAG, __VA_ARGS__)
#define DIFF_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DIFF_LOG_TAG, __VA_ARGS__)

using namespace MNN::DIFFUSION;

namespace aclaw {

DiffusionSession::DiffusionSession(std::string resourcePath, int memoryMode, int backendType)
    : resourcePath_(std::move(resourcePath)),
      memoryMode_(memoryMode),
      backendType_(backendType) {

    auto forwardType = static_cast<MNNForwardType>(backendType);

    DIFF_LOGI("Creating Diffusion: path=%s memoryMode=%d backend=%d",
              resourcePath_.c_str(), memoryMode_, backendType_);

    auto d = std::make_unique<MNN_DIFFUSION_Diffusion>();
    d->ptr.reset(Diffusion::createDiffusion(
        resourcePath_,
        DiffusionModelType::STABLE_DIFFUSION_1_5,
        forwardType,
        memoryMode_
    ));

    if (!d->ptr) {
        DIFF_LOGE("createDiffusion returned nullptr");
        return;
    }

    DIFF_LOGI("Diffusion object created, calling load()...");
    bool loadOk = d->ptr->load();
    if (!loadOk) {
        DIFF_LOGE("Diffusion::load() returned false");
        return;
    }

    loaded_ = true;
    diffusion_ = std::move(d);
    DIFF_LOGI("Diffusion models loaded successfully");
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
        if (!diffusion_->ptr->load()) {
            DIFF_LOGE("Failed to reload");
            return;
        }
        loaded_ = true;
    }

    DIFF_LOGI("Starting generation: prompt='%s' output='%s' steps=%d seed=%d",
              prompt.c_str(), outputPath.c_str(), iterNum, randomSeed);

    diffusion_->ptr->run(prompt, outputPath, iterNum, randomSeed, progressCallback);

    DIFF_LOGI("Generation complete, output: %s", outputPath.c_str());
}

} // namespace aclaw