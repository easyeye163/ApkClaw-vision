package com.apk.claw.android.local.diffusion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * MNN-Diffusion 本地文生图引擎
 *
 * 基于 MNN 推理引擎的 Stable Diffusion 1.5 文生图功能。
 * 需要先运行 scripts/build_mnn.sh 编译 libMNN.so 并准备模型文件。
 *
 * 使用方式:
 *   val engine = DiffusionEngine.getInstance(context)
 *   engine.generate("a cute cat", steps = 20) { progress ->
 *       Log.d("Diffusion", "Progress: $progress%")
 *   }
 */
class DiffusionEngine private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "DiffusionEngine"

        // MMKV keys
        private const val KEY_MODEL_PATH = "diffusion_model_path"
        private const val KEY_BACKEND_TYPE = "diffusion_backend_type" // 0=CPU, 3=OpenCL, 4=Auto(deprecated)
        private const val KEY_MEMORY_MODE = "diffusion_memory_mode"   // 0=saving, 1=enough, 2=balance
        private const val KEY_STEPS = "diffusion_steps"               // default 20
        private const val KEY_MIGRATED_BACKEND = "diffusion_backend_migrated"

        // Backend types (matching MNNForwardType: CPU=0, METAL=1, CUDA=2, OPENCL=3, AUTO=4)
        const val BACKEND_CPU = 0
        const val BACKEND_OPENCL = 3  // MNN_FORWARD_OPENCL = 3 (was incorrectly set to 4 = MNN_FORWARD_AUTO)
        const val BACKEND_AUTO = 4

        // Memory modes
        const val MEMORY_SAVING = 0
        const val MEMORY_ENOUGH = 1
        const val MEMORY_BALANCE = 2

        // Default generation params
        const val DEFAULT_STEPS = 20
        const val DEFAULT_WIDTH = 512
        const val DEFAULT_HEIGHT = 512

        @Volatile
        private var instance: DiffusionEngine? = null

        fun getInstance(context: Context): DiffusionEngine {
            return instance ?: synchronized(this) {
                instance ?: DiffusionEngine(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // State
    // ───────────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow<DiffusionState>(DiffusionState.Uninitialized)
    val state: StateFlow<DiffusionState> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value is DiffusionState.Ready
    val isGenerating: Boolean get() = _state.value is DiffusionState.Generating

    @Volatile
    private var nativePtr: Long = 0

    // ───────────────────────────────────────────────────────────────────────
    // Coroutine scope
    // ───────────────────────────────────────────────────────────────────────

    private val diffusionDispatcher: CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(diffusionDispatcher + SupervisorJob())

    // ───────────────────────────────────────────────────────────────────────
    // Config (persisted via MMKV)
    // ───────────────────────────────────────────────────────────────────────

    private val kv: MMKV by lazy { MMKV.defaultMMKV() }

    var modelPath: String
        get() = kv.decodeString(KEY_MODEL_PATH, "") ?: ""
        set(value) { kv.encode(KEY_MODEL_PATH, value) }

    /**
     * Effective backend with automatic fallback:
     * - Migrates old BACKEND_AUTO(4) to BACKEND_OPENCL(3)
     * - Falls back to CPU if OpenCL is not available on device
     */
    var backendType: Int
        get() {
            // One-time migration: old code saved 4 (AUTO) for OpenCL, now corrected to 3
            if (!kv.decodeBoolForKey(KEY_MIGRATED_BACKEND, false)) {
                val old = kv.decodeInt(KEY_BACKEND_TYPE, -1)
                if (old == 4) {
                    kv.encode(KEY_BACKEND_TYPE, BACKEND_OPENCL)
                    Log.i(TAG, "Migrated backend type: 4(AUTO) -> 3(OPENCL)")
                }
                kv.encode(KEY_MIGRATED_BACKEND, true)
            }

            val saved = kv.decodeInt(KEY_BACKEND_TYPE, BACKEND_OPENCL)

            // Normalize: any value > BACKEND_OPENCL that isn't explicitly AUTO should be treated as OpenCL
            val effective = when (saved) {
                BACKEND_CPU -> BACKEND_CPU
                BACKEND_OPENCL, BACKEND_AUTO -> BACKEND_OPENCL
                else -> {
                    Log.w(TAG, "Unknown backend type $saved, defaulting to CPU")
                    BACKEND_CPU
                }
            }

            // If OpenCL requested, check if device actually supports it
            if (effective == BACKEND_OPENCL && !isNativeLibLoaded()) {
                Log.w(TAG, "Native lib not loaded, cannot use OpenCL, falling back to CPU")
                return BACKEND_CPU
            }
            if (effective == BACKEND_OPENCL && !isOpenCLSupported()) {
                Log.w(TAG, "OpenCL not available on this device, falling back to CPU")
                return BACKEND_CPU
            }
            return effective
        }
        set(value) { kv.encode(KEY_BACKEND_TYPE, value) }

    var memoryMode: Int
        get() = kv.decodeInt(KEY_MEMORY_MODE, MEMORY_SAVING)
        set(value) { kv.encode(KEY_MEMORY_MODE, value) }

    var defaultSteps: Int
        get() = kv.decodeInt(KEY_STEPS, DEFAULT_STEPS)
        set(value) { kv.encode(KEY_STEPS, value) }

    // ───────────────────────────────────────────────────────────────────────
    // Native methods (JNI)
    // ───────────────────────────────────────────────────────────────────────

    private external fun nativeInit(
        resourcePath: String,
        memoryMode: Int,
        backendType: Int
    ): Long

    private external fun nativeGenerate(
        instanceId: Long,
        prompt: String,
        outputPath: String,
        iterNum: Int,
        randomSeed: Int,
        progressListener: ProgressListener
    ): HashMap<String, Any>?

    private external fun nativeRelease(instanceId: Long)

    // ───────────────────────────────────────────────────────────────────────
    // Initialization
    // ───────────────────────────────────────────────────────────────────────

    init {
        scope.launch {
            try {
                _state.value = DiffusionState.Initializing
                System.loadLibrary("apkclaw_diffusion")
                nativeLibLoaded = true
                _state.value = DiffusionState.NativeLoaded
                Log.i(TAG, "Diffusion native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Diffusion native library not available (MNN not built yet)", e)
                nativeLibLoaded = false
                _state.value = DiffusionState.NativeNotAvailable(
                    "MNN-Diffusion 未编译。请运行 scripts/build_mnn.sh 构建依赖。"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load diffusion native library", e)
                nativeLibLoaded = false
                _state.value = DiffusionState.Error(e)
            }
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // Public API
    // ───────────────────────────────────────────────────────────────────────

    /**
     * 加载 Diffusion 模型
     *
     * @param path 模型文件目录路径（包含 MNN 格式的 SD 1.5 模型文件）
     */
    suspend fun loadModel(path: String? = null) {
        withContext(diffusionDispatcher) {
            val modelDir = path ?: modelPath
            if (modelDir.isBlank()) {
                _state.value = DiffusionState.Error(
                    RuntimeException("模型路径未设置，请在设置中配置 MNN-Diffusion 模型路径")
                )
                return@withContext
            }

            val modelFile = File(modelDir)
            if (!modelFile.exists() || !modelFile.isDirectory) {
                _state.value = DiffusionState.Error(
                    RuntimeException("模型目录不存在: $modelDir")
                )
                return@withContext
            }

            try {
                _state.value = DiffusionState.LoadingModel
                Log.i(TAG, "Loading diffusion model from: $modelDir")

                // Release previous instance if any
                if (nativePtr != 0L) {
                    nativeRelease(nativePtr)
                    nativePtr = 0
                }

                nativePtr = nativeInit(modelDir, memoryMode, backendType)
                if (nativePtr == 0L) {
                    throw RuntimeException("nativeInit returned 0 - model loading failed")
                }

                if (path != null) {
                    modelPath = path
                }

                _state.value = DiffusionState.Ready
                Log.i(TAG, "Diffusion model loaded successfully (ptr=$nativePtr)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load diffusion model", e)
                _state.value = DiffusionState.Error(e)
                throw e
            }
        }
    }

    /**
     * 生成图片
     *
     * @param prompt 文本提示词（英文，SD 1.5 模型）
     * @param steps 去噪步数（10-50，推荐 20）
     * @param seed 随机种子（-1 表示随机）
     * @param onProgress 进度回调 (0-100)
     * @return 生成的图片 Bitmap，失败返回 null
     */
    suspend fun generate(
        prompt: String,
        steps: Int = defaultSteps,
        seed: Int = -1,
        onProgress: ((Int) -> Unit)? = null
    ): Bitmap? {
        return withContext(diffusionDispatcher) {
            if (nativePtr == 0L) {
                Log.e(TAG, "Model not loaded, cannot generate")
                _state.value = DiffusionState.Error(
                    RuntimeException("模型未加载，请先加载 MNN-Diffusion 模型")
                )
                return@withContext null
            }

            if (prompt.isBlank()) {
                Log.e(TAG, "Empty prompt")
                return@withContext null
            }

            _state.value = DiffusionState.Generating(0)

            // Create output file in app's cache directory
            val outputFile = File(context.cacheDir, "diffusion_${System.currentTimeMillis()}.png")

            val progressListener = object : ProgressListener {
                override fun onProgress(progress: Int) {
                    _state.value = DiffusionState.Generating(progress)
                    onProgress?.invoke(progress)
                }
            }

            try {
                Log.i(TAG, "Generating image: prompt='$prompt' steps=$steps seed=$seed")

                val result = nativeGenerate(
                    nativePtr, prompt, outputFile.absolutePath,
                    steps, seed, progressListener
                )

                if (result == null) {
                    Log.e(TAG, "nativeGenerate returned null")
                    _state.value = DiffusionState.Ready
                    return@withContext null
                }

                val success = result["success"] as? Boolean ?: false
                val totalTimeMs = result["totalTimeMs"] as? Long ?: 0

                Log.i(TAG, "Generation complete: success=$success time=${totalTimeMs}ms")

                if (success && outputFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    _state.value = DiffusionState.Ready
                    bitmap
                } else {
                    val error = result["error"] as? String ?: "Unknown error"
                    Log.e(TAG, "Generation failed: $error")
                    _state.value = DiffusionState.Error(RuntimeException(error))
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation exception", e)
                _state.value = DiffusionState.Error(e)
                null
            } finally {
                // Clean up output file after reading
                outputFile.delete()
            }
        }
    }

    /**
     * 生成图片并保存到文件
     *
     * @return 保存的文件路径，失败返回 null
     */
    suspend fun generateToFile(
        prompt: String,
        outputFile: File,
        steps: Int = defaultSteps,
        seed: Int = -1,
        onProgress: ((Int) -> Unit)? = null
    ): String? {
        return withContext(diffusionDispatcher) {
            if (nativePtr == 0L) return@withContext null

            _state.value = DiffusionState.Generating(0)

            val progressListener = object : ProgressListener {
                override fun onProgress(progress: Int) {
                    _state.value = DiffusionState.Generating(progress)
                    onProgress?.invoke(progress)
                }
            }

            try {
                val result = nativeGenerate(
                    nativePtr, prompt, outputFile.absolutePath,
                    steps, seed, progressListener
                )

                val success = result?.get("success") as? Boolean ?: false
                if (success && outputFile.exists()) {
                    _state.value = DiffusionState.Ready
                    outputFile.absolutePath
                } else {
                    _state.value = DiffusionState.Ready
                    null
                }
            } catch (e: Exception) {
                _state.value = DiffusionState.Error(e)
                null
            }
        }
    }

    /**
     * 卸载模型释放内存
     */
    suspend fun unloadModel() {
        withContext(diffusionDispatcher) {
            if (nativePtr != 0L) {
                nativeRelease(nativePtr)
                nativePtr = 0
                _state.value = DiffusionState.NativeLoaded
                Log.i(TAG, "Diffusion model unloaded")
            }
        }
    }

    /**
     * 释放所有资源
     */
    fun shutdown() {
        scope.launch {
            if (nativePtr != 0L) {
                nativeRelease(nativePtr)
                nativePtr = 0
            }
            _state.value = DiffusionState.Uninitialized
            Log.i(TAG, "Diffusion engine shut down")
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // OpenCL availability detection
    // ───────────────────────────────────────────────────────────────────────

    /** Whether the native library has been successfully loaded */
    private var nativeLibLoaded = false

    private fun isNativeLibLoaded(): Boolean = nativeLibLoaded

    /**
     * Runtime check for OpenCL support via JNI.
     * Safe to call multiple times — result is cached.
     */
    private var openCLSupported: Boolean? = null

    private fun isOpenCLSupported(): Boolean {
        openCLSupported?.let { return it }
        var supported = false
        try {
            supported = nativeCheckOpenCL()
        } catch (_: Exception) {
            Log.w(TAG, "OpenCL check failed")
        }
        openCLSupported = supported
        Log.i(TAG, "OpenCL supported: $supported")
        return supported
    }

    private external fun nativeCheckOpenCL(): Boolean

    // ───────────────────────────────────────────────────────────────────────
    // Progress listener interface (called from native code via JNI)
    // ───────────────────────────────────────────────────────────────────────

    interface ProgressListener {
        fun onProgress(progress: Int)
    }
}