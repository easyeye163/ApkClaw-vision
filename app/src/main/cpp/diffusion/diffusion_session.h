//
// diffusion_session.h
// MNN-Diffusion JNI bridge for ApkClaw
//

#pragma once

#include <string>
#include <functional>
#include <memory>
#include "diffusion/diffusion.hpp"

// Wrapper to hold MNN Diffusion pointer
class MNN_DIFFUSION_Diffusion {
public:
    std::unique_ptr<MNN::DIFFUSION::Diffusion> ptr;
};

namespace aclaw {

class DiffusionSession {
public:
    explicit DiffusionSession(std::string resourcePath, int memoryMode, int backendType);
    ~DiffusionSession();

    bool isLoaded() const { return loaded_; }

    void run(const std::string& prompt,
             const std::string& outputPath,
             int iterNum,
             int randomSeed,
             const std::function<void(int)>& progressCallback);

private:
    bool loaded_ = false;
    std::string resourcePath_;
    int memoryMode_;
    int backendType_;
    std::unique_ptr<MNN_DIFFUSION_Diffusion> diffusion_;
};

} // namespace aclaw