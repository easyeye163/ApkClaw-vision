//
// diffusion_session.cpp
// MNN-Diffusion implementation for ApkClaw
//

#include "diffusion_session.h"
#include <android/log.h>
#include <sys/stat.h>

#define DIFF_LOG_TAG "ApkClawDiffusion"
#define DIFF_LOGI(...) __android_log_print(ANDROID_LOG_INFO, DIFF_LOG_TAG, __VA_ARGS__)
#define DIFF_LOGW(...) __android_log_print(ANDROID_LOG_WARN, DIFF_LOG_TAG, __VA_ARGS__)
#define DIFF_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, DIFF_LOG_TAG, __VA_ARGS__)

using namespace MNN::DIFFUSION;

namespace aclaw {

static bool fileExists(const std::string& path) {
    struct stat st;
    return (stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode));
}

static void diagnoseModelFiles(const std::string& dir) {
    const char* required[] = {
        "text_encoder.mnn", "text_encoder.mnn.weight",
        "unet.mnn", "unet.mnn.weight",
        "vae_decoder.mnn", "vae_decoder.mnn.weight",
        "tokenizer.mtok", nullptr
    };
    for (int i = 0; required[i]; i++) {
        std::string full = dir + "/" + required[i];
        if (fileExists(full)) {
            struct stat st;
            stat(full.c_str(), &st);
            DIFF_LOGI("  [OK] %s (%ld bytes)", required[i], (long)st.st_size);
        } else {
            DIFF_LOGE("  [MISSING] %s", required[i]);
        }
    }
}

// Check if MNN_DIFFUSION_WITH_LLM_TOKENIZER was defined at compile time
// by checking a symbol that only exists when the macro is defined.
// The MtokTokenizer::load() with the macro calls MNN::Transformer::Tokenizer::createTokenizer,
// which is in the llm target. Without the macro, it just returns false.
// We check at runtime by calling the tokenizer's load and seeing the error.
#ifdef MNN_DIFFUSION_WITH_LLM_TOKENIZER
#define HAS_LLM_TOKENIZER 1
#else
#define HAS_LLM_TOKENIZER 0
#endif

DiffusionSession::DiffusionSession(std::string resourcePath, int memoryMode, int backendType)
    : resourcePath_(std::move(resourcePath)),
      memoryMode_(memoryMode),
      backendType_(backendType) {

    auto forwardType = static_cast<MNNForwardType>(backendType);

    DIFF_LOGI("Creating Diffusion: path=%s memoryMode=%d backend=%d",
              resourcePath_.c_str(), memoryMode_, backendType_);

    // Diagnostic: check all required model files
    DIFF_LOGI("Diagnosing model files in %s:", resourcePath_.c_str());
    diagnoseModelFiles(resourcePath_);

    // Diagnostic: check compile-time macro
    DIFF_LOGI("MNN_DIFFUSION_WITH_LLM_TOKENIZER defined: %s", HAS_LLM_TOKENIZER ? "YES" : "NO");

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
    DIFF_LOGI("NOTE: MNN internal errors use tag 'MNNJNI' — check: adb logcat -s MNNJNI");
    bool loadOk = d->ptr->load();
    if (!loadOk) {
        DIFF_LOGE("Diffusion::load() returned false");
        DIFF_LOGE("  Possible causes:");
        DIFF_LOGE("  1. MNN_DIFFUSION_WITH_LLM_TOKENIZER not defined (check HAS_LLM_TOKENIZER above)");
        DIFF_LOGE("  2. tokenizer.mtok missing or corrupted");
        DIFF_LOGE("  3. Model .mnn files corrupted");
        DIFF_LOGE("  Run: adb logcat -s MNNJNI to see MNN's internal error message");
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