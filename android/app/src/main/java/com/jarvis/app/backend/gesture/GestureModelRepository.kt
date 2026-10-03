package com.jarvis.app.backend.gesture

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor

/**
 * Provides the official MediaPipe gesture model without putting a binary model
 * in the source tree. A future release may bundle the same file in assets.
 */
object GestureModelRepository {
    private const val MODEL_NAME = "gesture_recognizer.task"
    private const val MAX_MODEL_BYTES = 32L * 1024L * 1024L
    private const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task"

    fun modelFile(context: Context): File = File(context.filesDir, MODEL_NAME)

    fun ensureModel(
        context: Context,
        executor: Executor,
        onStatus: (String) -> Unit,
        onReady: (Result<File>) -> Unit,
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val target = modelFile(appContext)
            try {
                if (target.isFile && target.length() > 1024L) {
                    onReady(Result.success(target))
                    return@execute
                }
                target.delete()
                val partial = File(target.parentFile, "$MODEL_NAME.part")
                partial.delete()
                onStatus("Downloading the on-device gesture model…")
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 45_000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        error("Model download failed: HTTP ${connection.responseCode}")
                    }
                    val advertised = connection.contentLengthLong
                    if (advertised > MAX_MODEL_BYTES) error("Model is unexpectedly large")
                    BufferedInputStream(connection.inputStream).use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > MAX_MODEL_BYTES) error("Model is unexpectedly large")
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                    if (partial.length() <= 1024L) error("Downloaded model is empty")
                    if (!partial.renameTo(target)) error("Could not install gesture model")
                    onReady(Result.success(target))
                } finally {
                    connection.disconnect()
                    partial.delete()
                }
            } catch (t: Throwable) {
                target.delete()
                onReady(Result.failure(t))
            }
        }
    }
}
