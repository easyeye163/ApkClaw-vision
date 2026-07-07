package com.apk.claw.android.local.diffusion

/**
 * MNN-Diffusion 引擎状态
 */
sealed class DiffusionState {
    /** 初始状态，native 库尚未加载 */
    object Uninitialized : DiffusionState()

    /** 正在加载 native 库 */
    object Initializing : DiffusionState()

    /** Native 库加载成功，但模型未加载 */
    object NativeLoaded : DiffusionState()

    /** Native 库不可用（MNN 未编译） */
    data class NativeNotAvailable(val message: String) : DiffusionState()

    /** 正在加载模型文件 */
    object LoadingModel : DiffusionState()

    /** 模型就绪，可以生成 */
    object Ready : DiffusionState()

    /** 正在生成图片，progress 0-100 */
    data class Generating(val progress: Int) : DiffusionState()

    /** 出错 */
    data class Error(val exception: Throwable) : DiffusionState()
}