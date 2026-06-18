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

    private var downloadJob: Job? = null
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

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
        dismissLoadingDialog()
    }
}