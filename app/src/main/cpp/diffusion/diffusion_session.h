//
// diffusion_session.h
// MNN-Diffusion JNI bridge for ApkClaw
//
// NOTE: Full compilation requires MNN headers in mnn_include/ (after running build_mnn.sh).
// The actual MNN::DIFFUSION::Diffusion type is only used in diffusion_session.cpp.
//

#pragma once

#include <string>
#include <functional>
#include <memory>

namespace aclaw {

class DiffusionSession {
public:
    explicit DiffusionSession(std::string resourcePath, int memoryMode, int backendType);
    ~DiffusionSession();

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
    // Opaque pointer to MNN::DIFFUSION::Diffusion — defined in diffusion_session.cpp
    std::unique_ptr<class MNN_DIFFUSION_Diffusion> diffusion_;
};

} // namespace aclaw