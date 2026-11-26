package com.pocketworker.app.core.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.concurrent.thread

class LocalModelManagerTest {

    private lateinit var tempDir: File
    private lateinit var serverSocket: ServerSocket
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("local-model-test").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverSocket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    }

    @After
    fun tearDown() {
        scope.cancel()
        runCatching { serverSocket.close() }
        tempDir.deleteRecursively()
    }

    @Test
    fun `downloads model across HTTP redirect, reports progress, resumes partial file, and deletes cleanly`() {
        val payload = ByteArray(128 * 1024) { idx -> (idx % 251).toByte() }
        val port = serverSocket.localPort

        // Minimal local HTTP server handling 302 redirect and 206 Range resume
        val serverThread = thread(isDaemon = true) {
            while (!serverSocket.isClosed) {
                val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
                runCatching {
                    client.use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.US_ASCII))
                        val requestLine = reader.readLine() ?: return@use
                        val path = requestLine.split(' ').getOrNull(1).orEmpty()
                        var rangeHeader: String? = null
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Range:", ignoreCase = true)) {
                                rangeHeader = line.substringAfter(':').trim()
                            }
                        }

                        val out = sock.getOutputStream()
                        when (path) {
                            "/redirect" -> {
                                val resp = "HTTP/1.1 302 Found\r\n" +
                                    "Location: http://127.0.0.1:$port/file.litertlm\r\n" +
                                    "Content-Length: 0\r\n" +
                                    "Connection: close\r\n\r\n"
                                out.write(resp.toByteArray(StandardCharsets.US_ASCII))
                                out.flush()
                            }
                            "/file.litertlm" -> {
                                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                                    val start = rangeHeader.removePrefix("bytes=").substringBefore('-').toIntOrNull() ?: 0
                                    val slice = payload.copyOfRange(start, payload.size)
                                    val header = "HTTP/1.1 206 Partial Content\r\n" +
                                        "Content-Range: bytes $start-${payload.size - 1}/${payload.size}\r\n" +
                                        "Content-Length: ${slice.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    out.write(header.toByteArray(StandardCharsets.US_ASCII))
                                    out.write(slice)
                                    out.flush()
                                } else {
                                    val header = "HTTP/1.1 200 OK\r\n" +
                                        "Content-Length: ${payload.size}\r\n" +
                                        "Connection: close\r\n\r\n"
                                    out.write(header.toByteArray(StandardCharsets.US_ASCII))
                                    out.write(payload)
                                    out.flush()
                                }
                            }
                        }
                    }
                }
            }
        }

        val removedIds = mutableListOf<String>()
        val spec = LocalModelSpec(
            id = "gemma-4-E2B-it",
            displayName = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            downloadUrl = "http://127.0.0.1:$port/redirect",
            expectedSizeBytes = payload.size.toLong()
        )
        val modelsDir = File(tempDir, "models")
        val manager = LocalModelManager(
            modelsDir = modelsDir,
            scope = scope,
            specs = listOf(spec),
            onModelRemoved = { removedIds.add(it) }
        )

        // Pre-seed partial file to test Range resume (206 Partial Content)
        modelsDir.mkdirs()
        File(modelsDir, "gemma-4-E2B-it.litertlm.part").writeBytes(payload.copyOfRange(0, 32 * 1024))
        manager.refreshStates()
        assertEquals(32L * 1024L, manager.states.value[spec.id]?.downloadedBytes)
        assertFalse(manager.isModelDownloaded(spec.id))

        manager.startDownload(spec.id)

        // Wait for download completion
        var attempts = 0
        while (manager.states.value[spec.id]?.status != LocalModelStatus.DOWNLOADED && attempts < 100) {
            Thread.sleep(30)
            attempts++
        }

        val doneState = manager.states.value[spec.id]!!
        assertEquals(LocalModelStatus.DOWNLOADED, doneState.status)
        assertEquals(1f, doneState.progress, 0.001f)
        assertTrue(manager.isModelDownloaded(spec.id))
        assertArrayEquals(payload, manager.getDownloadedModelFile(spec.id)!!.readBytes())
        assertFalse(File(modelsDir, "gemma-4-E2B-it.litertlm.part").exists())

        // Delete removes file and triggers engine release callback
        manager.deleteModel(spec.id)
        assertFalse(manager.isModelDownloaded(spec.id))
        assertEquals(listOf(spec.id), removedIds)
        assertEquals(LocalModelStatus.NOT_DOWNLOADED, manager.states.value[spec.id]?.status)

        serverSocket.close()
        serverThread.join(1000)
    }
}
