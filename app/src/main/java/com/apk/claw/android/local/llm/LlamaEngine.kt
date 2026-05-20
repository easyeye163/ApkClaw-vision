package com.apk.claw.android.local.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation

/**
 * 本地 LLM 引擎（JNI 封装）
 *
 * 管理 llama.cpp native 库的生命周期，提供模型加载、推理、卸载等操作。
 * 采用单例 + 独立单线程调度器模式，确保所有 native 调用都在同一个线程上执行。
 *
 * 使用方式:
 *   val engine = LlamaEngine.getInstance(context)
 *   engine.loadModel(modelPath, mmprojPath)
 *   engine.setSystemPrompt("You are a helpful assistant.")
 *   engine.sendUserPrompt("Hello").collect { token -> print(token) }
 */
class LlamaEngine private constructor(
    private val context: Context,
    private val nativeLibDir: String
) {

    companion object {
        private const val TAG = "LlamaEngine"
        const val DEFAULT_PREDICT_LENGTH = 512

        @Volatile
        private var instance: LlamaEngine? = null

        fun getInstance(context: Context): LlamaEngine {
            return instance ?: synchronized(this) {
                val libDir = context.applicationInfo.nativeLibraryDir
                check(libDir.isNotBlank()) { "Expected a valid native library path!" }
                LlamaEngine(context, libDir).also {
                    instance = it
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // State
    // ───────────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow<LlamaState>(LlamaState.Uninitialized)
    val state: StateFlow<LlamaState> = _state.asStateFlow()

    val isModelLoaded: Boolean get() = _state.value is LlamaState.ModelReady

    @Volatile
    var _mmprojLoaded: Boolean = false
        private set

    @Volatile
    private var _cancelGeneration: Boolean = false

    @Volatile
    private var _readyForSystemPrompt: Boolean = false

    // ───────────────────────────────────────────────────────────────────────
    // Coroutine scope & dispatcher
    // ───────────────────────────────────────────────────────────────────────

    val llamaDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    val llamaScope: CoroutineScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    // ───────────────────────────────────────────────────────────────────────
    // Native methods (JNI)
    // ───────────────────────────────────────────────────────────────────────

    private external fun init(nativeLibDir: String)
    private external fun systemInfo(): String
    private external fun load(modelPath: String): Int
    private external fun loadMmproj(mmprojPath: String, imageMaxSliceNums: Int): Int
    private external fun prepare(): Int
    private external fun processSystemPrompt(systemPrompt: String): Int
    private external fun prefillImage(imageData: ByteArray, imageSize: Int): Int
    private external fun processUserPrompt(userPrompt: String, predictLength: Int): Int
    private external fun generateNextToken(): String?
    private external fun nativeCancelGeneration()
    private external fun nativeFullReset()
    private external fun shutdown()
    private external fun unload()
    private external fun getMinicpmvVersionNative(): Int
    private external fun setMinicpmvVersionNative(version: Int)
    private external fun setImageMaxSliceNumsNative(n: Int)

    // ───────────────────────────────────────────────────────────────────────
    // Initialization (auto-load native libs on construction)
    // ───────────────────────────────────────────────────────────────────────

    init {
        llamaScope.launch {
            try {
                check(_state.value is LlamaState.Uninitialized) {
                    "Cannot load native library in ${_state.value::class.simpleName}!"
                }

                _state.value = LlamaState.Initializing
                Log.i(TAG, "Loading native library...")
                Log.i(TAG, CpuFeatures.summary())

                // 尝试加载优化版 ggml-cpu
                val bestVariant = CpuFeatures.bestGgmlCpuVariant()
                if (bestVariant != null) {
                    try {
                        Log.i(TAG, "Pre-loading optimised ggml-cpu ($bestVariant)")
                        System.loadLibrary("ggml-cpu-$bestVariant")
                        Log.i(TAG, "Optimised ggml-cpu ($bestVariant) loaded successfully")
                    } catch (e: UnsatisfiedLinkError) {
                        Log.w(TAG, "Optimised ggml-cpu-$bestVariant not available, using baseline", e)
                    }
                }

                System.loadLibrary("apkclaw_llama")
                init(nativeLibDir)

                _state.value = LlamaState.Initialized
                Log.i(TAG, "Native library loaded! System info:\n${systemInfo()}")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Native library not available in this build. On-device inference is disabled.", e)
                _state.value = LlamaState.Error(
                    RuntimeException("Native library not available. On-device inference requires NDK build with llama.cpp submodule.")
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load native library", e)
                _state.value = LlamaState.Error(e)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Public API (suspend functions, all run on llamaDispatcher)
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 加载 GGUF 模型文件（可选加载 mmproj 多模态投影层）
     *
     * @param pathToModel GGUF 模型文件路径
     * @param pathToMmproj mmproj 文件路径（多模态模型需要，纯文本模型传 null）
     */
    suspend fun loadModel(pathToModel: String, pathToMmproj: String? = null) {
        withContext(llamaDispatcher) {
            require(_state.value is LlamaState.Initialized || _state.value is LlamaState.ModelReady) {
                "Engine must be Initialized or ModelReady to load model, current: ${_state.value::class.simpleName}"
            }

            _state.value = LlamaState.LoadingModel
            Log.i(TAG, "Loading model: $pathToModel")

            try {
                val ret = load(pathToModel)
                if (ret != 0) {
                    throw RuntimeException("Failed to load model (ret=$ret)")
                }

                // 加载 mmproj（如果有）
                if (pathToMmproj != null) {
                    Log.i(TAG, "Loading mmproj: $pathToMmproj")
                    val mmprojRet = loadMmproj(pathToMmproj, 1)
                    if (mmprojRet != 0) {
                        Log.w(TAG, "Failed to load mmproj (ret=$mmprojRet), vision will be unavailable")
                        _mmprojLoaded = false
                    } else {
                        _mmprojLoaded = true
                        Log.i(TAG, "mmproj loaded successfully, vision support enabled")
                    }
                } else {
                    _mmprojLoaded = false
                }

                _state.value = LlamaState.ModelReady
                _readyForSystemPrompt = true
                Log.i(TAG, "Model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                _state.value = LlamaState.Error(e)
                throw e
            }
        }
    }

    /**
     * 设置 system prompt（每次新对话前调用）
     */
    suspend fun setSystemPrompt(prompt: String) {
        withContext(llamaDispatcher) {
            _state.value = LlamaState.ProcessingSystemPrompt
            val ret = processSystemPrompt(prompt)
            if (ret != 0) {
                throw RuntimeException("Failed to process system prompt (ret=$ret)")
            }
            _state.value = LlamaState.ModelReady
        }
    }

    /**
     * 预填充图像（vision 模型使用）
     */
    suspend fun prefillImage(imageData: ByteArray) {
        withContext(llamaDispatcher) {
            _state.value = LlamaState.PrefillingImage
            val ret = prefillImage(imageData, imageData.size)
            if (ret != 0) {
                throw RuntimeException("Failed to prefill image (ret=$ret)")
            }
            _state.value = LlamaState.ModelReady
        }
    }

    /**
     * 发送用户 prompt 并生成回复（流式）
     *
     * @param message 用户输入
     * @param predictLength 最大生成 token 数
     * @return Flow<String> 逐 token 输出的回复流
     */
    fun sendUserPrompt(message: String, predictLength: Int = DEFAULT_PREDICT_LENGTH): Flow<String> {
        return flow {
            _state.value = LlamaState.ProcessingUserPrompt
            _cancelGeneration = false

            val ret = processUserPrompt(message, predictLength)
            if (ret != 0) {
                throw RuntimeException("Failed to process user prompt (ret=$ret)")
            }

            _state.value = LlamaState.Generating

            // 逐 token 生成
            while (!_cancelGeneration) {
                val token = generateNextToken() ?: break
                emit(token)
            }

            _state.value = LlamaState.ModelReady
        }.flowOn(llamaDispatcher)
    }

    /**
     * 取消当前生成
     */
    suspend fun cancelGeneration() {
        withContext(llamaDispatcher) {
            _cancelGeneration = true
            nativeCancelGeneration()
            _state.value = LlamaState.ModelReady
        }
    }

    /**
     * 完全重置上下文（重新开始对话）
     */
    suspend fun fullReset() {
        withContext(llamaDispatcher) {
            nativeFullReset()
            _readyForSystemPrompt = true
            _state.value = LlamaState.ModelReady
        }
    }

    /**
     * 卸载模型（释放 native 内存）
     */
    suspend fun unloadModel() {
        withContext(llamaDispatcher) {
            _state.value = LlamaState.UnloadingModel
            try {
                unload()
                _mmprojLoaded = false
                _readyForSystemPrompt = false
                _state.value = LlamaState.Initialized
                Log.i(TAG, "Model unloaded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unload model", e)
                _state.value = LlamaState.Error(e)
            }
        }
    }

    /**
     * 关闭引擎（释放所有 native 资源）
     */
    fun shutdownEngine() {
        llamaScope.launch {
            try {
                shutdown()
                Log.i(TAG, "Engine shut down")
            } catch (e: Exception) {
                Log.e(TAG, "Error during shutdown", e)
            }
        }
    }
}
