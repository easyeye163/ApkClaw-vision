package com.apk.claw.android.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.apk.claw.android.R
import com.apk.claw.android.base.BaseActivity
import com.apk.claw.android.utils.KVUtils
import com.apk.claw.android.widget.CommonToolbar
import com.apk.claw.android.widget.KButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class LocalModelConfigActivity : BaseActivity() {

    companion object {
        private const val TAG = "LocalModelConfig"
        // MNN-Diffusion 模型在 ModelScope 的仓库 ID
        private const val DIFFUSION_MS_REPO = "MNN/stable-diffusion-v1-5-mnn-opencl"
        // 模型文件在本地的存储目录名
        private const val DIFFUSION_MODEL_DIR = "diffusion_sd1.5_mnn"
    }

    private lateinit var tvModelStatus: TextView
    private lateinit var tvModelInfo: TextView
    private lateinit var progressDownload: ProgressBar
    private lateinit var tvDownloadProgress: TextView
    private lateinit var btnDownload: KButton
    private lateinit var btnLoadModel: KButton
    private lateinit var btnDeleteModel: KButton
    private lateinit var btnSaveParams: KButton
    private lateinit var btnSaveServerConfig: KButton
    private lateinit var etBaseUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var seekbarTemperature: SeekBar
    private lateinit var seekbarMaxTokens: SeekBar
    private lateinit var tvTemperatureValue: TextView
    private lateinit var tvMaxTokensValue: TextView
    private lateinit var etCustomRepo: EditText
    private lateinit var btnAddCustom: KButton
    private lateinit var btnRemoveCustom: KButton

    // Diffusion UI views
    private lateinit var tvDiffusionStatus: TextView
    private lateinit var tvDiffusionInfo: TextView
    private lateinit var progressDiffusionDownload: ProgressBar
    private lateinit var tvDiffusionDownloadProgress: TextView
    private lateinit var btnDiffusionDownload: com.apk.claw.android.widget.KButton
    private lateinit var btnDiffusionLoad: com.apk.claw.android.widget.KButton
    private lateinit var btnDiffusionDelete: com.apk.claw.android.widget.KButton
    private lateinit var seekbarDiffusionSteps: SeekBar
    private lateinit var tvDiffusionStepsValue: TextView
    private lateinit var spinnerDiffusionBackend: android.widget.Spinner

    private var downloadJob: Job? = null
    private var diffusionDownloadJob: Job? = null
    private var isDiffusionModelLoaded: Boolean = false
    private var isModelLoaded: Boolean = false
    private var selectedModel: LocalModelInfo = LocalModelInfo.DEFAULT_MODEL
    private lateinit var allModels: List<LocalModelInfo>
    private var adapter: LocalModelAdapter? = null

    private val modelsBaseDir: File
        get() = File(filesDir, "local_models")

    // ModelScope 下载专用：大超时，应对大文件慢速下载
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
    // API 请求专用（文件发现等小请求）
    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_model_config)

        val toolbar = findViewById<CommonToolbar>(R.id.toolbar)
        toolbar.setTitle(getString(R.string.local_model_config_title))
        toolbar.showBackButton(true) { finish() }

        tvModelStatus = findViewById(R.id.tv_model_status)
        tvModelInfo = findViewById(R.id.tv_model_info)
        progressDownload = findViewById(R.id.progress_download)
        tvDownloadProgress = findViewById(R.id.tv_download_progress)
        btnDownload = findViewById(R.id.btn_download)
        btnLoadModel = findViewById(R.id.btn_load_model)
        btnDeleteModel = findViewById(R.id.btn_delete_model)
        btnSaveParams = findViewById(R.id.btn_save_params)
        btnSaveServerConfig = findViewById(R.id.btn_save_server_config)
        etBaseUrl = findViewById(R.id.et_base_url)
        etApiKey = findViewById(R.id.et_api_key)
        seekbarTemperature = findViewById(R.id.seekbar_temperature)
        seekbarMaxTokens = findViewById(R.id.seekbar_max_tokens)
        tvTemperatureValue = findViewById(R.id.tv_temperature_value)
        tvMaxTokensValue = findViewById(R.id.tv_max_tokens_value)
        etCustomRepo = findViewById(R.id.et_custom_repo)
        btnAddCustom = findViewById(R.id.btn_add_custom)
        btnRemoveCustom = findViewById(R.id.btn_remove_custom)

        setupModelList()
        setupSeekBarListeners()
        loadSavedParams()
        loadServerConfig()
        updateUI()

        btnDownload.setOnClickListener { startDownload() }
        btnLoadModel.setOnClickListener { loadModel() }
        btnDeleteModel.setOnClickListener { confirmDelete() }
        btnSaveParams.setOnClickListener { saveParams() }
        btnSaveServerConfig.setOnClickListener { saveServerConfig() }
        btnAddCustom.setOnClickListener { addCustomModel() }
        btnRemoveCustom.setOnClickListener { removeCustomModel() }

        // ── Diffusion views ──
        tvDiffusionStatus = findViewById(R.id.tv_diffusion_status)
        tvDiffusionInfo = findViewById(R.id.tv_diffusion_info)
        progressDiffusionDownload = findViewById(R.id.progress_diffusion_download)
        tvDiffusionDownloadProgress = findViewById(R.id.tv_diffusion_download_progress)
        btnDiffusionDownload = findViewById(R.id.btn_diffusion_download)
        btnDiffusionLoad = findViewById(R.id.btn_diffusion_load)
        btnDiffusionDelete = findViewById(R.id.btn_diffusion_delete)
        seekbarDiffusionSteps = findViewById(R.id.seekbar_diffusion_steps)
        tvDiffusionStepsValue = findViewById(R.id.tv_diffusion_steps_value)
        spinnerDiffusionBackend = findViewById(R.id.spinner_diffusion_backend)

        // 后端选择 Spinner
        val backendLabels = arrayOf("OpenCL (GPU 推荐)", "CPU")
        val backendAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, backendLabels)
        spinnerDiffusionBackend.adapter = backendAdapter

        // Steps SeekBar
        seekbarDiffusionSteps.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tvDiffusionStepsValue.text = (progress.coerceAtLeast(1)).toString()
            }
        })

        btnDiffusionDownload.setOnClickListener { startDiffusionDownload() }
        btnDiffusionLoad.setOnClickListener { loadDiffusionModel() }
        btnDiffusionDelete.setOnClickListener { confirmDeleteDiffusion() }
        findViewById<com.apk.claw.android.widget.KButton>(R.id.btn_save_diffusion_config).setOnClickListener { saveDiffusionConfig() }

        // 恢复已保存的 diffusion 配置
        restoreDiffusionConfig()

        // 自动扫描本地已下载的 diffusion 模型
        autoScanDiffusionModel()
        updateDiffusionUI()
    }

    private fun setupModelList() {
        allModels = LocalModelInfo.getAllModels(this)
        val savedId = KVUtils.getLocalModelId()
        selectedModel = allModels.find { it.id == savedId } ?: LocalModelInfo.DEFAULT_MODEL

        adapter = LocalModelAdapter(
            allModels,
            selectedModel.id
        ) { m ->
            selectedModel = m
            KVUtils.setLocalModelId(m.id)
            updateUI()
            Toast.makeText(this, getString(R.string.local_model_selected, m.displayName), Toast.LENGTH_SHORT).show()
        }

        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_models)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    /** Refresh the model list (after adding/removing custom models) */
    private fun refreshModelList() {
        allModels = LocalModelInfo.getAllModels(this)
        adapter = LocalModelAdapter(allModels, selectedModel.id) { m ->
            selectedModel = m
            KVUtils.setLocalModelId(m.id)
            updateUI()
            Toast.makeText(this, getString(R.string.local_model_selected, m.displayName), Toast.LENGTH_SHORT).show()
        }
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_models)
        recyclerView.adapter = adapter
    }

    /**
     * Add a custom model by discovering GGUF files from a ModelScope repo.
     * Uses ModelScope API to list files and download.
     */
    private fun addCustomModel() {
        val repoId = etCustomRepo.text.toString().trim()
        if (repoId.isBlank()) {
            Toast.makeText(this, "请输入仓库 ID", Toast.LENGTH_SHORT).show()
            return
        }
        // Validate format: should contain at least one /
        if (!repoId.contains("/")) {
            Toast.makeText(this, "格式错误，应为 用户名/仓库名，如 ggml-org/Qwen2.5-Omni-3B-GGUF", Toast.LENGTH_LONG).show()
            return
        }

        btnAddCustom.isEnabled = false
        btnAddCustom.text = "发现中..."
        tvModelStatus.text = "正在发现仓库文件..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val files = discoverRepoFiles(repoId)
                if (files.ggufFiles.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LocalModelConfigActivity, "未找到 GGUF 文件，请检查仓库名", Toast.LENGTH_LONG).show()
                        tvModelStatus.text = getString(R.string.local_model_status_not_ready)
                        btnAddCustom.isEnabled = true
                        btnAddCustom.text = "添加"
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    showGgufPickerDialog(repoId, files)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LocalModelConfigActivity, "发现失败: ${e.message}", Toast.LENGTH_LONG).show()
                    tvModelStatus.text = getString(R.string.local_model_status_not_ready)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    btnAddCustom.isEnabled = true
                    btnAddCustom.text = "添加"
                }
            }
        }
    }

    /** Repo file discovery result */
    private data class RepoFiles(
        val ggufFiles: List<GgufFileEntry>,
        val mmprojFiles: List<GgufFileEntry>
    )

    private data class GgufFileEntry(
        val fileName: String,
        val size: Long
    )

    /**
     * Discover GGUF files from a repo using ModelScope API.
     * Tries both "master" and "main" branches.
     */
    private fun discoverRepoFiles(repoId: String): RepoFiles {
        val branches = listOf("master", "main")
        var lastError: Exception? = null

        for (branch in branches) {
            try {
                val url = "https://modelscope.cn/api/v1/models/$repoId/repo/files?path=/&branch=$branch"
                val request = Request.Builder().url(url).build()
                val response = apiClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    lastError = RuntimeException("HTTP ${response.code}")
                    continue
                }
                val body = response.body?.string() ?: continue
                response.close()

                val rootObj = org.json.JSONObject(body)
                val dataObj = rootObj.optJSONObject("Data") ?: continue
                val filesArray = dataObj.optJSONArray("Files") ?: continue

                val ggufFiles = mutableListOf<GgufFileEntry>()
                val mmprojFiles = mutableListOf<GgufFileEntry>()

                for (i in 0 until filesArray.length()) {
                    val obj = filesArray.getJSONObject(i)
                    val name = obj.getString("Name")
                    val size = obj.optLong("Size", 0L)
                    val lowerName = name.lowercase()

                    if (lowerName.endsWith(".gguf") && !lowerName.contains("mmproj")) {
                        ggufFiles.add(GgufFileEntry(name, size))
                    } else if (lowerName.contains("mmproj") && lowerName.endsWith(".gguf")) {
                        mmprojFiles.add(GgufFileEntry(name, size))
                    }
                }

                if (ggufFiles.isNotEmpty()) {
                    return RepoFiles(ggufFiles, mmprojFiles)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw lastError ?: RuntimeException("未在 ModelScope 找到 GGUF 文件，请检查仓库名")
    }

    /** Show a dialog to pick which GGUF file to use */
    private fun showGgufPickerDialog(repoId: String, files: RepoFiles) {
        // Sort GGUF files: prefer Q4_K_M, then Q4, then others; smaller files first within same priority
        val sorted = files.ggufFiles.sortedWith(compareByDescending<GgufFileEntry> {
            when {
                it.fileName.lowercase().contains("q4_k_m") -> 100
                it.fileName.lowercase().contains("q4_k_s") -> 90
                it.fileName.lowercase().contains("q4_0") -> 80
                it.fileName.lowercase().contains("q5_k_m") -> 70
                it.fileName.lowercase().contains("q3_k_m") -> 60
                it.fileName.lowercase().contains("q2_k") -> 50
                else -> 0
            }
        }.thenBy { it.size })

        // Build display list
        val displayNames = sorted.map { f ->
            val sizeStr = formatFileSize(f.size)
            val recommended = if (f.fileName.lowercase().contains("q4_k_m")) " [推荐]" else ""
            "$f.fileName ($sizeStr)$recommended"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择 GGUF 文件")
            .setItems(displayNames) { _, which ->
                val chosen = sorted[which]
                createCustomModel(repoId, chosen, files.mmprojFiles)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Create and save a custom model from discovered files */
    private fun createCustomModel(
        repoId: String,
        ggufEntry: GgufFileEntry,
        mmprojFiles: List<GgufFileEntry>
    ) {
        // Generate a safe ID from repo name
        val safeId = repoId.replace("/", "-").replace(".", "-").lowercase()
            .replace(Regex("[^a-z0-9-]"), "-").replace(Regex("-+"), "-").trim('-')

        // Try to find matching mmproj
        val mmprojFile = mmprojFiles.firstOrNull()

        val model = LocalModelInfo(
            id = "$safeId-${System.currentTimeMillis() % 100000}",
            displayName = repoId.substringAfterLast("/"),
            description = "自定义模型: $repoId",
            ggufFileName = ggufEntry.fileName,
            mmprojFileName = mmprojFile?.fileName,
            hfRepo = repoId,
            msRepo = repoId,  // Same repo ID usually works on both platforms
            modelSize = "~${formatFileSize(ggufEntry.size)}",
            isCustom = true
        )

        LocalModelInfo.addCustomModel(this, model)
        selectedModel = model
        KVUtils.setLocalModelId(model.id)
        etCustomRepo.text.clear()
        refreshModelList()
        updateUI()

        val mmprojInfo = if (mmprojFile != null) "\nmmproj: ${mmprojFile.fileName}" else ""
        Toast.makeText(this, "已添加: ${model.displayName}\n${ggufEntry.fileName}$mmprojInfo", Toast.LENGTH_LONG).show()
    }

    /** Remove the currently selected custom model */
    private fun removeCustomModel() {
        if (!selectedModel.isCustom) return
        AlertDialog.Builder(this)
            .setTitle("删除自定义模型")
            .setMessage("确定删除 ${selectedModel.displayName}？已下载的文件也会被删除。")
            .setPositiveButton("确定") { _, _ ->
                LocalModelInfo.removeCustomModel(this, selectedModel.id)
                // Also delete downloaded files
                val modelDir = File(modelsBaseDir, selectedModel.id)
                if (modelDir.exists()) modelDir.deleteRecursively()

                selectedModel = LocalModelInfo.DEFAULT_MODEL
                KVUtils.setLocalModelId(selectedModel.id)
                refreshModelList()
                updateUI()
                isModelLoaded = false
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupSeekBarListeners() {
        seekbarTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tvTemperatureValue.text = "%.2f".format(progress / 100.0)
            }
        })

        seekbarMaxTokens.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                tvMaxTokensValue.text = progress.toString()
            }
        })
    }

    private fun loadSavedParams() {
        val temperature = KVUtils.getLocalModelTemperature()
        val maxTokens = KVUtils.getLocalModelMaxTokens()

        seekbarTemperature.progress = (100 * temperature).coerceIn(0.0, 200.0).toInt()
        seekbarMaxTokens.progress = maxTokens.coerceIn(1, 4096)
        tvTemperatureValue.text = "%.2f".format(temperature)
        tvMaxTokensValue.text = maxTokens.toString()
    }

    private fun saveParams() {
        val temperature = seekbarTemperature.progress / 100.0
        val maxTokens = seekbarMaxTokens.progress
        KVUtils.setLocalModelTemperature(temperature)
        KVUtils.setLocalModelMaxTokens(maxTokens)
        Toast.makeText(this, getString(R.string.local_model_params_saved), Toast.LENGTH_SHORT).show()
    }

    private fun loadServerConfig() {
        etBaseUrl.setText(KVUtils.getLocalModelBaseUrl())
        etApiKey.setText(KVUtils.getLocalModelApiKey())
    }

    private fun saveServerConfig() {
        val baseUrl = etBaseUrl.text.toString().trim()
        val apiKey = etApiKey.text.toString().trim()
        if (baseUrl.isNotEmpty()) {
            KVUtils.setLocalModelBaseUrl(baseUrl)
        }
        if (apiKey.isNotEmpty()) {
            KVUtils.setLocalModelApiKey(apiKey)
        }
        Toast.makeText(this, getString(R.string.local_model_server_config_saved), Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val modelDir = File(modelsBaseDir, selectedModel.id)
        val ggufFile = File(modelDir, selectedModel.ggufFileName)
        val mmprojFile = selectedModel.mmprojFileName?.let { File(modelDir, it) }
        val isGgufOk = ggufFile.exists()
        val isMmprojOk = mmprojFile == null || mmprojFile.exists()
        val isDownloaded = isGgufOk && isMmprojOk
        val downloadedSize = ggufFile.length() + (if (mmprojFile?.exists() == true) mmprojFile.length() else 0L)

        // Show/hide delete custom button
        btnRemoveCustom.visibility = if (selectedModel.isCustom) View.VISIBLE else View.GONE

        // Update status text
        when {
            isModelLoaded -> {
                tvModelStatus.text = getString(R.string.local_model_status_ready, selectedModel.displayName)
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
            }
            isDownloaded -> {
                tvModelStatus.setText(getString(R.string.local_model_status_downloaded))
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
            }
            else -> {
                tvModelStatus.setText(getString(R.string.local_model_status_not_ready))
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
            }
        }

        // Update model info and delete button visibility
        if (isDownloaded) {
            tvModelInfo.visibility = View.VISIBLE
            tvModelInfo.text = "%s\n%s: %s".format(
                selectedModel.ggufFileName,
                getString(R.string.local_model_file_size),
                formatFileSize(downloadedSize)
            )
            btnDeleteModel.visibility = View.VISIBLE
        } else {
            tvModelInfo.visibility = View.GONE
            btnDeleteModel.visibility = View.GONE
        }

        // Update download button text
        btnDownload.text = if (isDownloaded) {
            getString(R.string.local_model_redownload)
        } else {
            getString(R.string.local_model_download)
        }

        // Update load button
        btnLoadModel.isEnabled = isDownloaded
        btnLoadModel.text = if (isModelLoaded) {
            getString(R.string.local_model_reload)
        } else {
            getString(R.string.local_model_load)
        }
    }

    private fun startDownload() {
        if (downloadJob?.isActive == true) {
            Toast.makeText(this, getString(R.string.local_model_downloading), Toast.LENGTH_SHORT).show()
            return
        }

        val modelDir = File(modelsBaseDir, selectedModel.id)
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }

        btnDownload.isEnabled = false
        btnLoadModel.isEnabled = false
        progressDownload.visibility = View.VISIBLE
        progressDownload.progress = 0
        tvDownloadProgress.visibility = View.VISIBLE
        tvDownloadProgress.text = getString(R.string.local_model_preparing_download)
        tvModelStatus.text = getString(R.string.local_model_downloading_status)

        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                downloadFiles()
                withContext(Dispatchers.Main) {
                    tvModelStatus.text = getString(R.string.local_model_download_complete)
                    tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
                    tvDownloadProgress.text = getString(R.string.local_model_download_complete)
                    progressDownload.progress = progressDownload.max
                    updateUI()
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    tvModelStatus.text = getString(R.string.local_model_download_cancelled)
                    tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
                    updateUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvModelStatus.text = getString(R.string.local_model_download_failed)
                    tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
                    tvDownloadProgress.text = getString(R.string.local_model_download_failed_detail, e.message ?: "")
                    updateUI()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressDownload.visibility = View.GONE
                    tvDownloadProgress.visibility = View.GONE
                }
            }
        }
    }

    private suspend fun downloadFiles() {
        val ggufFileName = selectedModel.ggufFileName
        val modelDir = File(modelsBaseDir, selectedModel.id)
        if (!modelDir.exists()) modelDir.mkdirs()
        val ggufUrls = buildGgufUrls()

        downloadSingleFile(ggufFileName, modelDir, ggufUrls)

        // 验证 GGUF 文件已下载
        val ggufFile = File(modelDir, ggufFileName)
        if (!ggufFile.exists()) {
            throw RuntimeException(getString(R.string.local_model_download_failed))
        }

        val mmprojFileName = selectedModel.mmprojFileName
        if (mmprojFileName != null) {
            val mmprojFile = File(modelDir, mmprojFileName)
            if (!mmprojFile.exists()) {
                val mmprojUrls = buildMmprojUrls()
                downloadSingleFile(mmprojFileName, modelDir, mmprojUrls)

                // 验证 mmproj 文件已下载
                if (!mmprojFile.exists()) {
                    throw RuntimeException(getString(R.string.local_model_mmproj_download_failed))
                }
            }
        }
    }

    private suspend fun downloadSingleFile(fileName: String, targetDir: File, urls: List<String>) {
        for (url in urls) {
            try {
                withContext(Dispatchers.Main) {
                    tvDownloadProgress.text = getString(R.string.local_model_downloading_file, fileName)
                }

                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    response.close()
                    continue
                }

                val body = response.body
                if (body == null) {
                    response.close()
                    continue
                }

                val contentLength = body.contentLength()
                val tempFile = File(targetDir, "$fileName.tmp")
                val finalFile = File(targetDir, fileName)

                // Remove existing final file if redownloading
                if (finalFile.exists()) {
                    finalFile.delete()
                }

                var bytesRead = 0L
                var lastUpdateTime = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateTime >= 200) {
                                lastUpdateTime = now
                                if (contentLength > 0) {
                                    val progress = ((bytesRead * 100) / contentLength).toInt()
                                    withContext(Dispatchers.Main) {
                                        progressDownload.progress = progress
                                        tvDownloadProgress.text = getString(R.string.local_model_download_progress, fileName, formatFileSize(bytesRead), formatFileSize(contentLength))
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        tvDownloadProgress.text = getString(R.string.local_model_download_progress_no_total, fileName, formatFileSize(bytesRead))
                                    }
                                }
                            }
                        }
                    }
                }

                response.close()

                // Rename temp file to final file
                if (tempFile.renameTo(finalFile)) {
                    withContext(Dispatchers.Main) {
                        tvDownloadProgress.text = getString(R.string.local_model_file_download_complete, fileName)
                    }
                    return
                } else {
                    // Rename failed, try copying
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        tvDownloadProgress.text = getString(R.string.local_model_file_download_complete, fileName)
                    }
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Try next URL
            }
        }

        // All URLs failed — throw so caller knows
        throw RuntimeException(getString(R.string.local_model_all_sources_failed, fileName))
    }

    private fun buildGgufUrls(): List<String> {
        val urls = mutableListOf<String>()
        selectedModel.directGgufUrl?.let { urls.add(it) }
        // 仅使用 ModelScope 下载
        selectedModel.msRepo?.let {
            urls.add("https://modelscope.cn/models/$it/resolve/master/${selectedModel.ggufFileName}")
            urls.add("https://modelscope.cn/models/$it/resolve/main/${selectedModel.ggufFileName}")
        }
        return urls
    }

    private fun buildMmprojUrls(): List<String> {
        val mmprojFileName = selectedModel.mmprojFileName ?: return emptyList()
        val urls = mutableListOf<String>()
        selectedModel.directMmprojUrl?.let { urls.add(it) }
        // 仅使用 ModelScope 下载
        selectedModel.msRepo?.let {
            urls.add("https://modelscope.cn/models/$it/resolve/master/$mmprojFileName")
            urls.add("https://modelscope.cn/models/$it/resolve/main/$mmprojFileName")
        }
        return urls
    }

    private var loadingDialog: AlertDialog? = null

    private fun showLoadingDialog(message: String) {
        if (loadingDialog?.isShowing == true) return
        val view = layoutInflater.inflate(R.layout.dialog_model_loading, null)
        val tvMsg = view.findViewById<TextView>(R.id.tv_loading_message)
        tvMsg?.text = message
        loadingDialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        loadingDialog?.show()
    }

    private fun updateLoadingDialog(message: String) {
        loadingDialog?.let { dialog ->
            val tvMsg = dialog.findViewById<TextView>(R.id.tv_loading_message)
            tvMsg?.text = message
        }
    }

    private fun dismissLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun loadModel() {
        val modelDir = File(modelsBaseDir, selectedModel.id)
        val ggufFile = File(modelDir, selectedModel.ggufFileName)
        val mmprojFile = selectedModel.mmprojFileName?.let { File(modelDir, it) }

        if (!ggufFile.exists()) {
            Toast.makeText(this, getString(R.string.local_model_please_download), Toast.LENGTH_LONG).show()
            return
        }
        // 多模态模型需要 mmproj 文件
        if (selectedModel.mmprojFileName != null && (mmprojFile == null || !mmprojFile.exists())) {
            Toast.makeText(this, getString(R.string.local_model_mmproj_missing), Toast.LENGTH_LONG).show()
            return
        }
        btnLoadModel.isEnabled = false
        tvModelStatus.text = getString(R.string.local_model_loading)
        tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
        showLoadingDialog(getString(R.string.local_model_loading))

        lifecycleScope.launch {
            try {
                android.util.Log.d(TAG, "loadModel: 获取引擎实例...")
                val engine = com.apk.claw.android.local.llm.LlamaEngine.getInstance(this@LocalModelConfigActivity)
                android.util.Log.d(TAG, "loadModel: 引擎实例获取成功，当前状态=${engine.state.value::class.simpleName}")

                // 等待引擎初始化完成（最多 30 秒）
                updateLoadingDialog(getString(R.string.local_model_init_engine))
                kotlinx.coroutines.withTimeoutOrNull(30_000) {
                    while (engine.state.value is com.apk.claw.android.local.llm.LlamaState.Uninitialized
                        || engine.state.value is com.apk.claw.android.local.llm.LlamaState.Initializing) {
                        kotlinx.coroutines.delay(200)
                    }
                }

                // 检查引擎是否就绪
                val currentState = engine.state.value
                android.util.Log.d(TAG, "loadModel: 引擎状态=${currentState::class.simpleName}")
                if (currentState is com.apk.claw.android.local.llm.LlamaState.Error) {
                    val errMsg = currentState.exception?.message ?: "Unknown error"
                    android.util.Log.e(TAG, "loadModel: 引擎初始化失败: $errMsg", currentState.exception)
                    throw RuntimeException(getString(R.string.local_model_engine_init_failed) + errMsg)
                }
                if (currentState !is com.apk.claw.android.local.llm.LlamaState.Initialized
                    && currentState !is com.apk.claw.android.local.llm.LlamaState.ModelReady) {
                    val stateName = currentState::class.simpleName ?: "Unknown"
                    android.util.Log.e(TAG, "loadModel: 引擎状态异常: $stateName")
                    throw RuntimeException(getString(R.string.local_model_engine_state_error) + stateName)
                }

                val mmprojFile = selectedModel.mmprojFileName?.let { File(File(modelsBaseDir, selectedModel.id), it) }

                // 加载模型（最多 120 秒超时）
                val loadMsg = getString(R.string.local_model_loading_model, selectedModel.displayName)
                updateLoadingDialog(loadMsg)
                android.util.Log.d(TAG, "loadModel: 开始加载模型文件 ${ggufFile.absolutePath}")
                kotlinx.coroutines.withTimeoutOrNull(120_000) {
                    engine.loadModel(ggufFile.absolutePath, mmprojFile?.absolutePath)
                } ?: run {
                    android.util.Log.e(TAG, "loadModel: 模型加载超时（120秒）")
                    throw RuntimeException(getString(R.string.local_model_load_timeout))
                }

                isModelLoaded = true
                KVUtils.setLocalModelChatActive(true)
                tvModelStatus.text = getString(R.string.local_model_status_ready, selectedModel.displayName)
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
                Toast.makeText(this@LocalModelConfigActivity, getString(R.string.local_model_load_success), Toast.LENGTH_LONG).show()
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.w(TAG, "loadModel: 已取消")
                tvModelStatus.text = getString(R.string.local_model_load_cancelled)
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
            } catch (e: Exception) {
                val errorMsg = e.message ?: "未知错误"
                android.util.Log.e(TAG, "loadModel failed", e)
                tvModelStatus.text = getString(R.string.local_model_load_failed_detail, errorMsg)
                tvModelStatus.setTextColor(getColor(R.color.colorErrorPrimary))
                AlertDialog.Builder(this@LocalModelConfigActivity)
                    .setTitle(getString(R.string.local_model_load_fail_title))
                    .setMessage(errorMsg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            dismissLoadingDialog()
            updateUI()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.local_model_delete_confirm_title))
            .setMessage(getString(R.string.local_model_delete_confirm_msg, selectedModel.displayName))
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ ->
                deleteModelFiles()
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    private fun deleteModelFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val modelDir = File(modelsBaseDir, selectedModel.id)
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            withContext(Dispatchers.Main) {
                isModelLoaded = false
                updateUI()
                Toast.makeText(this@LocalModelConfigActivity, getString(R.string.local_model_deleted), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1048576 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1073741824 -> "%.1f MB".format(bytes / 1048576.0)
            else -> "%.2f GB".format(bytes / 1073741824.0)
        }
    }

    // ───────────────────────────────────────────────────────────────────────
    // MNN-Diffusion 文生图模型
    // ───────────────────────────────────────────────────────────────────────

    /** Diffusion 模型在本地的存储目录 */
    private val diffusionModelDir: File
        get() = File(filesDir, DIFFUSION_MODEL_DIR)

    /** 检查 diffusion 模型目录是否包含所需文件（递归搜索子目录） */
    private fun isDiffusionModelDownloaded(): Boolean {
        val dir = diffusionModelDir
        if (!dir.exists() || !dir.isDirectory) return false
        // MNN-Diffusion SD 1.5 至少需要这些文件（可能在子目录中）
        val requiredFiles = listOf("clip_model.mnn", "unet_model.mnn", "vae_decoder_model.mnn")
        val existingNames = dir.walkTopDown().filter { it.isFile }.map { it.name }.toSet()
        return requiredFiles.all { it in existingNames }
    }

    /** 计算已下载模型的总大小 */
    private fun getDiffusionModelTotalSize(): Long {
        val dir = diffusionModelDir
        if (!dir.exists()) return 0L
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /** 更新 diffusion 模型区域的 UI 状态 */
    private fun updateDiffusionUI() {
        val downloaded = isDiffusionModelDownloaded()
        val engine = com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this)
        val engineReady = engine.state.value is com.apk.claw.android.local.diffusion.DiffusionState.Ready

        // 同步引擎实际状态
        if (engineReady && downloaded) {
            isDiffusionModelLoaded = true
        } else if (engine.state.value is com.apk.claw.android.local.diffusion.DiffusionState.NativeLoaded
            || engine.state.value is com.apk.claw.android.local.diffusion.DiffusionState.Uninitialized) {
            isDiffusionModelLoaded = false
        }

        // 状态文本
        when {
            isDiffusionModelLoaded -> {
                tvDiffusionStatus.text = getString(R.string.diffusion_model_status_ready)
                tvDiffusionStatus.setTextColor(getColor(R.color.colorTextSecondary))
            }
            downloaded -> {
                tvDiffusionStatus.text = getString(R.string.diffusion_model_status_downloaded)
                tvDiffusionStatus.setTextColor(getColor(R.color.colorTextSecondary))
            }
            else -> {
                tvDiffusionStatus.text = getString(R.string.diffusion_model_status_not_ready)
                tvDiffusionStatus.setTextColor(getColor(R.color.colorTextSecondary))
            }
        }

        // 文件信息
        if (downloaded) {
            val totalSize = getDiffusionModelTotalSize()
            tvDiffusionInfo.text = getString(R.string.diffusion_model_file_size, formatFileSize(totalSize))
            tvDiffusionInfo.visibility = View.VISIBLE
            btnDiffusionDelete.visibility = View.VISIBLE
        } else {
            tvDiffusionInfo.visibility = View.GONE
            btnDiffusionDelete.visibility = View.GONE
        }

        // 下载按钮
        btnDiffusionDownload.text = if (downloaded) {
            getString(R.string.diffusion_model_redownload)
        } else {
            getString(R.string.diffusion_model_download)
        }

        // 加载/卸载按钮
        btnDiffusionLoad.isEnabled = downloaded
        btnDiffusionLoad.text = if (isDiffusionModelLoaded) {
            getString(R.string.diffusion_model_unload)
        } else {
            getString(R.string.diffusion_model_load)
        }
    }

    /** 保存 diffusion 生图配置 */
    private fun saveDiffusionConfig() {
        val engine = com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this)
        engine.defaultSteps = seekbarDiffusionSteps.progress.coerceAtLeast(1)
        val backend = if (spinnerDiffusionBackend.selectedItemPosition == 0)
            com.apk.claw.android.local.diffusion.DiffusionEngine.BACKEND_OPENCL
        else
            com.apk.claw.android.local.diffusion.DiffusionEngine.BACKEND_CPU
        engine.backendType = backend
        Toast.makeText(this, getString(R.string.diffusion_model_config_saved), Toast.LENGTH_SHORT).show()
    }

    /** 恢复已保存的 diffusion 配置（steps、backend） */
    private fun restoreDiffusionConfig() {
        val engine = com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this)
        // 恢复 steps
        val savedSteps = engine.defaultSteps
        seekbarDiffusionSteps.progress = savedSteps.coerceIn(1, 50)
        tvDiffusionStepsValue.text = savedSteps.toString()
        // 恢复 backend
        val savedBackend = engine.backendType
        spinnerDiffusionBackend.setSelection(
            if (savedBackend == com.apk.claw.android.local.diffusion.DiffusionEngine.BACKEND_OPENCL) 0 else 1
        )
    }

    /**
     * 自动扫描本地已下载的 diffusion 模型文件。
     * 如果发现完整模型，自动将路径记录到 DiffusionEngine 的持久化配置中，
     * 并在 UI 上显示「已下载」状态，用户可以直接点击加载。
     */
    private fun autoScanDiffusionModel() {
        val dir = diffusionModelDir
        if (!dir.exists() || !dir.isDirectory) return
        if (!isDiffusionModelDownloaded()) return

        val engine = com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this)
        // 将模型目录路径持久化到 MMKV，下次进入可直接使用
        if (engine.modelPath != dir.absolutePath) {
            engine.modelPath = dir.absolutePath
        }
    }

    /**
     * 发现 ModelScope 仓库中 diffusion 模型的所有文件列表
     * 递归遍历子目录，返回相对于仓库根目录的路径列表
     */
    private fun discoverDiffusionFiles(repoId: String): List<DiffusionFileEntry> {
        val branches = listOf("master", "main")
        val allFiles = mutableListOf<DiffusionFileEntry>()

        for (branch in branches) {
            try {
                // 递归发现：先获取根目录，再遍历子目录
                discoverDirRecursive(repoId, branch, "", allFiles)
                if (allFiles.isNotEmpty()) return allFiles
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        return allFiles
    }

    /** 递归发现目录下的文件 */
    private fun discoverDirRecursive(
        repoId: String, branch: String, path: String, result: MutableList<DiffusionFileEntry>
    ) {
        val url = "https://modelscope.cn/api/v1/models/$repoId/repo/files?path=/$path&branch=$branch"
        val request = Request.Builder().url(url).build()
        val response = apiClient.newCall(request).execute()
        if (!response.isSuccessful) { response.close(); return }
        val body = response.body?.string() ?: run { response.close(); return }
        response.close()

        val rootObj = org.json.JSONObject(body)
        val dataObj = rootObj.optJSONObject("Data") ?: return
        val filesArray = dataObj.optJSONArray("Files") ?: return

        for (i in 0 until filesArray.length()) {
            val obj = filesArray.getJSONObject(i)
            val name = obj.getString("Name")
            val size = obj.optLong("Size", 0L)
            val type = obj.optString("Type", "File")

            val relativePath = if (path.isEmpty()) name else "$path/$name"

            if (type == "Directory" || obj.optBoolean("IsDir", false)) {
                // 递归进入子目录
                discoverDirRecursive(repoId, branch, relativePath, result)
            } else {
                result.add(DiffusionFileEntry(relativePath, name, size))
            }
        }
    }

    private data class DiffusionFileEntry(
        val relativePath: String,  // 相对于仓库根目录的路径
        val fileName: String,
        val size: Long
    )

    /** 开始下载 diffusion 模型 */
    private fun startDiffusionDownload() {
        if (diffusionDownloadJob?.isActive == true) {
            Toast.makeText(this, getString(R.string.local_model_downloading), Toast.LENGTH_SHORT).show()
            return
        }

        val dir = diffusionModelDir
        if (!dir.exists()) dir.mkdirs()

        btnDiffusionDownload.isEnabled = false
        progressDiffusionDownload.visibility = View.VISIBLE
        progressDiffusionDownload.progress = 0
        tvDiffusionDownloadProgress.visibility = View.VISIBLE
        tvDiffusionDownloadProgress.text = getString(R.string.diffusion_model_discovering)
        tvDiffusionStatus.text = getString(R.string.diffusion_model_downloading)

        diffusionDownloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 发现所有文件
                val files = discoverDiffusionFiles(DIFFUSION_MS_REPO)
                if (files.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LocalModelConfigActivity, getString(R.string.diffusion_model_no_files), Toast.LENGTH_LONG).show()
                        tvDiffusionStatus.text = getString(R.string.diffusion_model_status_not_ready)
                    }
                    return@launch
                }

                // 2. 逐文件下载
                val totalFiles = files.size
                val totalSize = files.sumOf { it.size }
                var downloadedSize = 0L

                for ((index, file) in files.withIndex()) {
                    // 跳过已存在且大小匹配的文件
                    val localFile = File(dir, file.relativePath)
                    val alreadyDownloaded = localFile.exists() && localFile.length() == file.size
                    if (alreadyDownloaded) {
                        downloadedSize += file.size
                    } else {
                        // 下载此文件
                        localFile.parentFile?.mkdirs()

                        withContext(Dispatchers.Main) {
                            tvDiffusionDownloadProgress.text = getString(
                                R.string.diffusion_model_downloading_file, file.fileName
                            )
                        }

                        val encodedPath = file.relativePath.replace(" ", "%20")
                        val urls = listOf(
                            "https://modelscope.cn/models/$DIFFUSION_MS_REPO/resolve/master/$encodedPath",
                            "https://modelscope.cn/models/$DIFFUSION_MS_REPO/resolve/main/$encodedPath"
                        )

                        downloadDiffusionSingleFile(urls, localFile, file.fileName)
                        if (localFile.exists()) {
                            downloadedSize += localFile.length()
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@LocalModelConfigActivity,
                                    getString(R.string.diffusion_model_download_failed, file.fileName),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    // Update progress
                    withContext(Dispatchers.Main) {
                        if (totalSize > 0) {
                            val progress = ((downloadedSize * 100) / totalSize).toInt()
                            progressDiffusionDownload.progress = progress
                            tvDiffusionDownloadProgress.text = getString(
                                R.string.diffusion_model_download_progress,
                                file.fileName, index + 1, totalFiles,
                                formatFileSize(downloadedSize), formatFileSize(totalSize)
                            )
                        } else {
                            tvDiffusionDownloadProgress.text = getString(
                                R.string.diffusion_model_download_progress_no_total,
                                file.fileName, index + 1, totalFiles,
                                formatFileSize(downloadedSize)
                            )
                        }
                    }
                }

                // 3. 下载完成 — 持久化模型路径
                val engine = com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this@LocalModelConfigActivity)
                engine.modelPath = dir.absolutePath

                withContext(Dispatchers.Main) {
                    progressDiffusionDownload.progress = progressDiffusionDownload.max
                    tvDiffusionDownloadProgress.text = getString(R.string.diffusion_model_download_complete)
                    tvDiffusionStatus.text = getString(R.string.diffusion_model_download_complete)
                    updateDiffusionUI()
                    Toast.makeText(this@LocalModelConfigActivity, getString(R.string.diffusion_model_download_complete), Toast.LENGTH_SHORT).show()
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    tvDiffusionStatus.text = getString(R.string.diffusion_model_download_cancelled)
                    updateDiffusionUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvDiffusionStatus.text = getString(R.string.diffusion_model_download_failed, e.message ?: "")
                    Toast.makeText(
                        this@LocalModelConfigActivity,
                        getString(R.string.diffusion_model_download_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                    updateDiffusionUI()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressDiffusionDownload.visibility = View.GONE
                    tvDiffusionDownloadProgress.visibility = View.GONE
                    btnDiffusionDownload.isEnabled = true
                }
            }
        }
    }

    /** 加载/卸载 diffusion 模型 */
    private fun loadDiffusionModel() {
        if (isDiffusionModelLoaded) {
            // 卸载
            lifecycleScope.launch {
                try {
                    val engine = com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this@LocalModelConfigActivity)
                    engine.unloadModel()
                    isDiffusionModelLoaded = false
                    updateDiffusionUI()
                    Toast.makeText(this@LocalModelConfigActivity, getString(R.string.diffusion_model_unload_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@LocalModelConfigActivity, "卸载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        if (!isDiffusionModelDownloaded()) {
            Toast.makeText(this, getString(R.string.local_model_please_download), Toast.LENGTH_LONG).show()
            return
        }

        btnDiffusionLoad.isEnabled = false
        tvDiffusionStatus.text = getString(R.string.diffusion_model_status_loading)

        lifecycleScope.launch {
            var errorMsg: String? = null
            try {
                val engine = com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this@LocalModelConfigActivity)

                // 检查 native 库是否可用
                when (val s = engine.state.value) {
                    is com.apk.claw.android.local.diffusion.DiffusionState.NativeNotAvailable -> {
                        errorMsg = s.message
                    }
                    is com.apk.claw.android.local.diffusion.DiffusionState.Error -> {
                        errorMsg = "引擎初始化异常，请重启应用后重试"
                    }
                    else -> {
                        // 保存模型路径并设置后端
                        engine.modelPath = diffusionModelDir.absolutePath
                        engine.backendType = if (spinnerDiffusionBackend.selectedItemPosition == 0)
                            com.apk.claw.android.local.diffusion.DiffusionEngine.BACKEND_OPENCL
                        else
                            com.apk.claw.android.local.diffusion.DiffusionEngine.BACKEND_CPU

                        // 加载模型（最多 120 秒超时）
                        val result = kotlinx.coroutines.withTimeoutOrNull(120_000) {
                            engine.loadModel()
                        }
                        if (result == null) {
                            errorMsg = "模型加载超时（120秒）"
                        } else {
                            isDiffusionModelLoaded = true
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                errorMsg = getString(R.string.diffusion_model_download_cancelled)
            } catch (e: Exception) {
                errorMsg = e.message ?: "Unknown error"
            }

            // 统一处理结果
            if (errorMsg != null) {
                // 加载失败 — 显示错误对话框，不调用 updateDiffusionUI 覆盖
                tvDiffusionStatus.text = getString(R.string.diffusion_model_status_loading_error, errorMsg)
                AlertDialog.Builder(this@LocalModelConfigActivity)
                    .setTitle("文生图模型加载失败")
                    .setMessage(errorMsg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                btnDiffusionLoad.isEnabled = true
            } else {
                // 加载成功
                tvDiffusionStatus.text = getString(R.string.diffusion_model_load_success)
                Toast.makeText(this@LocalModelConfigActivity, getString(R.string.diffusion_model_load_success), Toast.LENGTH_SHORT).show()
                updateDiffusionUI()
            }
        }
    }

    /** 确认删除 diffusion 模型 */
    private fun confirmDeleteDiffusion() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.diffusion_model_delete_confirm_title))
            .setMessage(getString(R.string.diffusion_model_delete_confirm_msg))
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    // 先卸载
                    if (isDiffusionModelLoaded) {
                        try {
                            com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this@LocalModelConfigActivity).unloadModel()
                        } catch (_: Exception) {}
                    }
                    val dir = diffusionModelDir
                    if (dir.exists()) dir.deleteRecursively()
                    // 清除持久化的模型路径
                    com.apk.claw.android.local.diffusion.DiffusionEngine.getInstance(this@LocalModelConfigActivity).modelPath = ""
                    withContext(Dispatchers.Main) {
                        isDiffusionModelLoaded = false
                        updateDiffusionUI()
                        Toast.makeText(this@LocalModelConfigActivity, getString(R.string.diffusion_model_deleted), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    /**
     * 下载单个 diffusion 模型文件（非协程上下文，可安全使用 continue/break）
     * 尝试多个 URL，成功则写入 localFile
     */
    private fun downloadDiffusionSingleFile(urls: List<String>, localFile: File, fileName: String) {
        var success = false
        for (url in urls) {
            if (success) return
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) { response.close(); return }
                val body = response.body
                if (body == null) { response.close(); return }

                val tempFile = File(localFile.parentFile, "${fileName}.tmp")
                if (localFile.exists()) localFile.delete()

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                response.close()

                if (!tempFile.renameTo(localFile)) {
                    tempFile.copyTo(localFile, overwrite = true)
                    tempFile.delete()
                }
                success = true
            } catch (_: Exception) {
                // try next URL
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        diffusionDownloadJob?.cancel()
        dismissLoadingDialog()
    }
}