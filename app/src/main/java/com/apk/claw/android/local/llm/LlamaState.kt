package com.apk.claw.android.local.llm

/**
 * LlamaEngine 状态机
 *
 * 定义本地模型引擎在各个生命周期阶段的状态，
 * 用于 UI 层观察和展示模型加载/推理进度。
 */
sealed class LlamaState {
    /** 初始状态，native 库尚未加载 */
    data object Uninitialized : LlamaState()

    /** 正在加载 native 库 */
    data object Initializing : LlamaState()

    /** native 库已加载，等待加载模型 */
    data object Initialized : LlamaState()

    /** 正在加载 GGUF 模型文件 */
    data object LoadingModel : LlamaState()

    /** 模型加载完毕，可以接收 system prompt */
    data object ModelReady : LlamaState()

    /** 正在处理 system prompt */
    data object ProcessingSystemPrompt : LlamaState()

    /** 正在预填充图像（vision 模型） */
    data object PrefillingImage : LlamaState()

    /** 正在处理用户 prompt */
    data object ProcessingUserPrompt : LlamaState()

    /** 正在生成 token */
    data object Generating : LlamaState()

    /** 正在卸载模型 */
    data object UnloadingModel : LlamaState()

    /** 错误状态 */
    data class Error(val exception: kotlin.Exception) : LlamaState()
}
