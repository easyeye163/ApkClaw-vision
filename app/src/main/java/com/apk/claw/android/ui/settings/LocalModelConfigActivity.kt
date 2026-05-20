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

    private var downloadJob: Job? = null
    private var isModelLoaded: Boolean = false
    private var selectedModel: LocalModelInfo = LocalModelInfo.DEFAULT_MODEL

    private val modelsBaseDir: File
        get() = File(filesDir, "local_models")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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
    }

    private fun setupModelList() {
        val savedId = KVUtils.getLocalModelId()
        selectedModel = LocalModelInfo.AVAILABLE_MODELS.find { it.id == savedId }
            ?: LocalModelInfo.DEFAULT_MODEL

        val adapter = LocalModelAdapter(
            LocalModelInfo.AVAILABLE_MODELS,
            selectedModel.id
        ) { m ->
            selectedModel = m
            KVUtils.setLocalModelId(m.id)
            updateUI()
            Toast.makeText(this, "已选择: ${m.displayName}", Toast.LENGTH_SHORT).show()
        }

        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler_models)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
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
        val isDownloaded = ggufFile.exists()
        val downloadedSize = ggufFile.length()

        // Update status text
        when {
            isModelLoaded -> {
                tvModelStatus.setText(getString(R.string.local_model_status_ready))
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
            Toast.makeText(this, "正在下载中...", Toast.LENGTH_SHORT).show()
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
        tvDownloadProgress.text = "准备下载..."
        tvModelStatus.text = "正在下载..."

        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                downloadFiles()
                withContext(Dispatchers.Main) {
                    tvModelStatus.text = "下载完成"
                    tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
                    tvDownloadProgress.text = "下载完成"
                    progressDownload.progress = progressDownload.max
                    updateUI()
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    tvModelStatus.text = "下载已取消"
                    tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
                    updateUI()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvModelStatus.text = "下载失败"
                    tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
                    tvDownloadProgress.text = "下载失败: ${e.message}"
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

        val mmprojFileName = selectedModel.mmprojFileName
        if (mmprojFileName != null) {
            val mmprojFile = File(modelDir, mmprojFileName)
            if (!mmprojFile.exists()) {
                val mmprojUrls = buildMmprojUrls()
                downloadSingleFile(mmprojFileName, modelDir, mmprojUrls)
            }
        }
    }

    private suspend fun downloadSingleFile(fileName: String, targetDir: File, urls: List<String>) {
        for (url in urls) {
            try {
                withContext(Dispatchers.Main) {
                    tvDownloadProgress.text = "正在下载: $fileName"
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
                                        tvDownloadProgress.text = "$fileName: ${formatFileSize(bytesRead)} / ${formatFileSize(contentLength)}"
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        tvDownloadProgress.text = "$fileName: ${formatFileSize(bytesRead)}"
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
                        tvDownloadProgress.text = "$fileName 下载完成"
                    }
                    return
                } else {
                    // Rename failed, try copying
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                    withContext(Dispatchers.Main) {
                        tvDownloadProgress.text = "$fileName 下载完成"
                    }
                    return
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Try next URL
            }
        }

        // All URLs failed
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@LocalModelConfigActivity,
                "下载失败: $fileName，所有镜像源均不可用",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun buildGgufUrls(): List<String> {
        val urls = mutableListOf<String>()
        selectedModel.directGgufUrl?.let { urls.add(it) }
        selectedModel.hfRepo?.let {
            urls.add("https://huggingface.co/$it/resolve/main/${selectedModel.ggufFileName}")
        }
        selectedModel.msRepo?.let {
            urls.add("https://modelscope.cn/models/$it/resolve/master/${selectedModel.ggufFileName}")
        }
        return urls
    }

    private fun buildMmprojUrls(): List<String> {
        val mmprojFileName = selectedModel.mmprojFileName ?: return emptyList()
        val urls = mutableListOf<String>()
        selectedModel.directMmprojUrl?.let { urls.add(it) }
        selectedModel.hfRepo?.let {
            urls.add("https://huggingface.co/$it/resolve/main/$mmprojFileName")
        }
        selectedModel.msRepo?.let {
            urls.add("https://modelscope.cn/models/$it/resolve/master/$mmprojFileName")
        }
        return urls
    }

    private fun loadModel() {
        val modelDir = File(modelsBaseDir, selectedModel.id)
        val ggufFile = File(modelDir, selectedModel.ggufFileName)
        if (!ggufFile.exists()) {
            Toast.makeText(this, getString(R.string.local_model_please_download), Toast.LENGTH_LONG).show()
            return
        }
        btnLoadModel.isEnabled = false
        tvModelStatus.text = "正在加载模型..."
        tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))

        lifecycleScope.launch {
            try {
                val engine = com.apk.claw.android.local.llm.LlamaEngine.getInstance(this@LocalModelConfigActivity)

                // 等待引擎初始化完成
                kotlinx.coroutines.withTimeoutOrNull(30_000) {
                    while (engine.state.value is com.apk.claw.android.local.llm.LlamaState.Uninitialized
                        || engine.state.value is com.apk.claw.android.local.llm.LlamaState.Initializing) {
                        kotlinx.coroutines.delay(200)
                    }
                }

                // 检查引擎是否就绪
                val currentState = engine.state.value
                if (currentState is com.apk.claw.android.local.llm.LlamaState.Error) {
                    throw RuntimeException("引擎初始化失败: ${currentState.exception.message}")
                }
                if (currentState !is com.apk.claw.android.local.llm.LlamaState.Initialized
                    && currentState !is com.apk.claw.android.local.llm.LlamaState.ModelReady) {
                    throw RuntimeException("引擎状态异常: ${currentState::class.simpleName}")
                }

                val mmprojFile = selectedModel.mmprojFileName?.let { File(File(modelsBaseDir, selectedModel.id), it) }

                engine.loadModel(ggufFile.absolutePath, mmprojFile?.absolutePath)

                isModelLoaded = true
                KVUtils.setLocalModelChatActive(true)
                tvModelStatus.text = "模型加载成功"
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
            } catch (e: kotlinx.coroutines.CancellationException) {
                tvModelStatus.text = "加载已取消"
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
            } catch (e: Exception) {
                tvModelStatus.text = "模型加载失败: ${e.message}"
                tvModelStatus.setTextColor(getColor(R.color.colorTextSecondary))
            }
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
                Toast.makeText(this@LocalModelConfigActivity, "模型已删除", Toast.LENGTH_SHORT).show()
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
    }
}
