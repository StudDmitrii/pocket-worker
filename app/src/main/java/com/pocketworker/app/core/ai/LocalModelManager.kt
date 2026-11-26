package com.pocketworker.app.core.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class LocalModelSpec(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedSizeBytes: Long
)

enum class LocalModelStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

data class LocalModelDownloadState(
    val modelId: String,
    val status: LocalModelStatus = LocalModelStatus.NOT_DOWNLOADED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * Manages on-device `.litertlm` model files for the Local (Gemma 4) AI provider:
 * supports resumable HTTP downloads with cross-host redirects, optional proxy,
 * live progress reporting, cancellation, and deletion.
 */
class LocalModelManager(
    private val modelsDir: File,
    private val scope: CoroutineScope,
    private val specs: List<LocalModelSpec> = DEFAULT_SPECS,
    private val onModelRemoved: (String) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val specsById: Map<String, LocalModelSpec> = specs.associateBy { it.id }
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow(buildInitialStates())
    val states: StateFlow<Map<String, LocalModelDownloadState>> = _states.asStateFlow()

    fun getSpec(modelId: String): LocalModelSpec? = specsById[modelId]

    fun getModelFile(modelId: String): File? {
        val spec = specsById[modelId] ?: return null
        return File(modelsDir, spec.fileName)
    }

    fun getDownloadedModelFile(modelId: String): File? {
        val file = getModelFile(modelId) ?: return null
        return if (file.isFile && file.length() > 0L) file else null
    }

    fun isModelDownloaded(modelId: String): Boolean = getDownloadedModelFile(modelId) != null

    fun hasAnyDownloadedModel(): Boolean = specs.any { isModelDownloaded(it.id) }

    fun refreshStates() {
        _states.update { current ->
            specs.associate { spec ->
                val existing = current[spec.id]
                if (existing?.status == LocalModelStatus.DOWNLOADING && activeJobs[spec.id]?.isActive == true) {
                    spec.id to existing
                } else {
                    spec.id to inspectDiskState(spec)
                }
            }
        }
    }

    fun startDownload(modelId: String, proxy: AiProxyConfig? = null) {
        val spec = specsById[modelId] ?: return
        if (activeJobs[modelId]?.isActive == true) return
        if (isModelDownloaded(modelId)) {
            updateState(modelId, inspectDiskState(spec))
            return
        }

        val job = scope.launch(ioDispatcher) {
            try {
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs()
                }
                val targetFile = File(modelsDir, spec.fileName)
                val partFile = File(modelsDir, "${spec.fileName}.part")
                var existingBytes = if (partFile.isFile) partFile.length() else 0L

                val neededSpace = (spec.expectedSizeBytes - existingBytes).coerceAtLeast(0L) + MIN_FREE_HEADROOM_BYTES
                val usable = modelsDir.usableSpace
                if (usable > 0L && usable < neededSpace) {
                    val neededGb = String.format(java.util.Locale.US, "%.2f", neededSpace / (1024.0 * 1024.0 * 1024.0))
                    val freeGb = String.format(java.util.Locale.US, "%.2f", usable / (1024.0 * 1024.0 * 1024.0))
                    updateState(
                        modelId,
                        LocalModelDownloadState(
                            modelId = modelId,
                            status = LocalModelStatus.ERROR,
                            downloadedBytes = existingBytes,
                            totalBytes = spec.expectedSizeBytes,
                            errorMessage = "Недостаточно памяти на устройстве: нужно ~$neededGb ГБ, свободно $freeGb ГБ."
                        )
                    )
                    return@launch
                }

                updateState(
                    modelId,
                    LocalModelDownloadState(
                        modelId = modelId,
                        status = LocalModelStatus.DOWNLOADING,
                        downloadedBytes = existingBytes,
                        totalBytes = spec.expectedSizeBytes
                    )
                )

                var conn = openDownloadConnection(spec.downloadUrl, existingBytes, proxy)
                try {
                    var code = conn.responseCode
                    if (code == 416 && existingBytes > 0L) {
                        // Stale partial file exceeds server range -> restart cleanly
                        conn.disconnect()
                        partFile.delete()
                        existingBytes = 0L
                        conn = openDownloadConnection(spec.downloadUrl, 0L, proxy)
                        code = conn.responseCode
                    }

                    if (code !in 200..299) {
                        throw IOException("Сервер загрузки вернул HTTP $code")
                    }

                    val appendMode = code == HttpURLConnection.HTTP_PARTIAL && existingBytes > 0L
                    if (!appendMode && existingBytes > 0L) {
                        partFile.delete()
                        existingBytes = 0L
                    }

                    val contentLength = conn.contentLengthLong
                    val totalBytes = when {
                        contentLength > 0L -> if (appendMode) existingBytes + contentLength else contentLength
                        else -> spec.expectedSizeBytes
                    }

                    var downloaded = existingBytes
                    var lastReportTime = 0L
                    var lastReportBytes = downloaded

                    updateState(
                        modelId,
                        LocalModelDownloadState(
                            modelId = modelId,
                            status = LocalModelStatus.DOWNLOADING,
                            downloadedBytes = downloaded,
                            totalBytes = totalBytes
                        )
                    )

                    conn.inputStream.use { input ->
                        FileOutputStream(partFile, appendMode).use { output ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                            while (true) {
                                if (!isActive) throw CancellationException("Download cancelled")
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                if (now - lastReportTime >= PROGRESS_THROTTLE_MS ||
                                    downloaded - lastReportBytes >= PROGRESS_THROTTLE_BYTES
                                ) {
                                    lastReportTime = now
                                    lastReportBytes = downloaded
                                    updateState(
                                        modelId,
                                        LocalModelDownloadState(
                                            modelId = modelId,
                                            status = LocalModelStatus.DOWNLOADING,
                                            downloadedBytes = downloaded,
                                            totalBytes = totalBytes
                                        )
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    if (downloaded <= 0L) {
                        throw IOException("Загруженный файл модели пуст.")
                    }
                    if (contentLength > 0L && downloaded < totalBytes) {
                        throw IOException("Загрузка прервалась не полностью. Нажмите «Скачать модель» для докачки.")
                    }

                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }

                    updateState(
                        modelId,
                        LocalModelDownloadState(
                            modelId = modelId,
                            status = LocalModelStatus.DOWNLOADED,
                            downloadedBytes = targetFile.length(),
                            totalBytes = targetFile.length()
                        )
                    )
                } finally {
                    conn.disconnect()
                }
            } catch (_: CancellationException) {
                updateState(modelId, inspectDiskState(spec))
            } catch (ex: Throwable) {
                val partFile = File(modelsDir, "${spec.fileName}.part")
                val partialBytes = if (partFile.isFile) partFile.length() else 0L
                updateState(
                    modelId,
                    LocalModelDownloadState(
                        modelId = modelId,
                        status = LocalModelStatus.ERROR,
                        downloadedBytes = partialBytes,
                        totalBytes = spec.expectedSizeBytes,
                        errorMessage = ex.localizedMessage ?: "Не удалось скачать модель."
                    )
                )
            } finally {
                activeJobs.remove(modelId)
            }
        }
        activeJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val spec = specsById[modelId] ?: return
        updateState(modelId, inspectDiskState(spec))
    }

    fun deleteModel(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        val spec = specsById[modelId] ?: return
        onModelRemoved(modelId)
        val file = File(modelsDir, spec.fileName)
        val partFile = File(modelsDir, "${spec.fileName}.part")
        if (file.exists()) file.delete()
        if (partFile.exists()) partFile.delete()
        updateState(
            modelId,
            LocalModelDownloadState(
                modelId = modelId,
                status = LocalModelStatus.NOT_DOWNLOADED,
                downloadedBytes = 0L,
                totalBytes = spec.expectedSizeBytes
            )
        )
    }

    private fun openDownloadConnection(
        initialUrl: String,
        rangeStartBytes: Long,
        proxy: AiProxyConfig?
    ): HttpURLConnection {
        if (proxy != null) {
            AiProxyAuthenticatorRegistry.register(proxy)
        }
        var currentUrl = initialUrl
        for (hop in 0 until MAX_REDIRECTS) {
            val url = URL(currentUrl)
            val conn = (if (proxy != null) {
                url.openConnection(proxy.toJavaProxy())
            } else {
                url.openConnection()
            }) as HttpURLConnection

            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connectTimeout = 20_000
            conn.readTimeout = 45_000
            conn.setRequestProperty("User-Agent", "PocketWorker-Android/1.0")
            if (rangeStartBytes > 0L) {
                conn.setRequestProperty("Range", "bytes=$rangeStartBytes-")
            }
            proxy?.basicAuthorizationHeader()?.let { auth ->
                conn.setRequestProperty("Proxy-Authorization", auth)
            }

            val code = conn.responseCode
            if (code in REDIRECT_CODES) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IOException("Некорректный редирект (HTTP $code) без заголовка Location.")
                }
                currentUrl = URL(url, location).toString()
                continue
            }
            return conn
        }
        throw IOException("Слишком много перенаправлений при загрузке модели.")
    }

    private fun buildInitialStates(): Map<String, LocalModelDownloadState> {
        return specs.associate { spec -> spec.id to inspectDiskState(spec) }
    }

    private fun inspectDiskState(spec: LocalModelSpec): LocalModelDownloadState {
        val file = File(modelsDir, spec.fileName)
        if (file.isFile && file.length() > 0L) {
            return LocalModelDownloadState(
                modelId = spec.id,
                status = LocalModelStatus.DOWNLOADED,
                downloadedBytes = file.length(),
                totalBytes = file.length()
            )
        }
        val partFile = File(modelsDir, "${spec.fileName}.part")
        val partialBytes = if (partFile.isFile) partFile.length() else 0L
        return LocalModelDownloadState(
            modelId = spec.id,
            status = LocalModelStatus.NOT_DOWNLOADED,
            downloadedBytes = partialBytes,
            totalBytes = spec.expectedSizeBytes
        )
    }

    private fun updateState(modelId: String, state: LocalModelDownloadState) {
        _states.update { current -> current + (modelId to state) }
    }

    companion object {
        const val MODEL_GEMMA_4_E2B_ID = "gemma-4-E2B-it"
        const val MODEL_GEMMA_4_E4B_ID = "gemma-4-E4B-it"

        private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_THROTTLE_MS = 160L
        private const val PROGRESS_THROTTLE_BYTES = 1024L * 1024L
        private const val MIN_FREE_HEADROOM_BYTES = 64L * 1024L * 1024L
        private const val MAX_REDIRECTS = 10
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        val DEFAULT_SPECS: List<LocalModelSpec> = listOf(
            LocalModelSpec(
                id = MODEL_GEMMA_4_E2B_ID,
                displayName = "Gemma 4 E2B",
                fileName = "gemma-4-E2B-it.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
                expectedSizeBytes = 2_588_147_712L
            ),
            LocalModelSpec(
                id = MODEL_GEMMA_4_E4B_ID,
                displayName = "Gemma 4 E4B",
                fileName = "gemma-4-E4B-it.litertlm",
                downloadUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
                expectedSizeBytes = 3_659_530_240L
            )
        )
    }
}
