package com.jarvis.gesturelab

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** Activity-scoped camera and recognizer. It only reports pointer observations. */
class GestureCameraSession(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(text: String)
        fun onModelProgress(progress: ModelDownloadProgress)
        fun onModelReady(bytes: Long)
        fun onFrameInfo(rotationDegrees: Int, width: Int, height: Int)
        fun onPointer(point: NormalizedPoint)
        fun onObservation(label: String?, confidence: Float)
        fun onError(text: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "gesture-motion-lab-camera").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val smoother = ExponentialPointSmoother(alpha = 0.5f)
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var recognizer: GestureRecognizer? = null
    private var modelBuffer: MappedByteBuffer? = null
    private var lastTimestampMs = 0L

    fun start(view: PreviewView) {
        if (closed.get()) return
        previewView = view
        postStatus("Preparing local gesture recognition…")
        GestureModelRepository.ensureModel(
            context = appContext,
            executor = executor,
            onStatus = ::postStatus,
            onProgress = ::postModelProgress,
            onReady = { result ->
                main.post {
                    if (closed.get()) return@post
                    result.fold(
                        onSuccess = { file ->
                            listener.onModelReady(file.length())
                            initializeRecognizer(file.absolutePath, view)
                        },
                        onFailure = { error ->
                            postError("Gesture model unavailable: ${error.message ?: "download failed"}")
                        },
                    )
                }
            },
        )
    }

    private fun initializeRecognizer(modelPath: String, view: PreviewView) {
        executor.execute {
            if (closed.get()) return@execute
            try {
                val mapped = FileInputStream(modelPath).use { input ->
                    input.channel.map(FileChannel.MapMode.READ_ONLY, 0L, input.channel.size())
                }
                modelBuffer = mapped
                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(mapped).build())
                    .setMinHandDetectionConfidence(0.65f)
                    .setMinHandPresenceConfidence(0.65f)
                    .setMinTrackingConfidence(0.65f)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result: GestureRecognizerResult, _ -> handleResult(result) }
                    .setErrorListener { error -> postError("Recognition error: ${error.message}") }
                    .build()
                recognizer = GestureRecognizer.createFromOptions(appContext, options)
                main.post { if (!closed.get()) bindCamera(view) }
            } catch (t: Throwable) {
                postError("Could not start recognition: ${t.message ?: "unknown error"}")
            }
        }
    }

    private fun bindCamera(view: PreviewView) {
        if (closed.get()) return
        val future = try { ProcessCameraProvider.getInstance(appContext) } catch (t: Throwable) {
            postError("Could not open camera: ${t.message ?: "camera unavailable"}")
            return
        }
        future.addListener({
            try {
                if (closed.get()) return@addListener
                val provider = future.get()
                cameraProvider = provider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis.setAnalyzer(executor) { image ->
                    try {
                        val bitmap = image.toBitmap()
                        val frameTime = max(SystemClock.uptimeMillis(), lastTimestampMs + 1L)
                        lastTimestampMs = frameTime
                        val rotation = image.imageInfo.rotationDegrees
                        postFrameInfo(rotation, bitmap.width, bitmap.height)
                        val processingOptions = ImageProcessingOptions.builder()
                            .setRotationDegrees(rotation)
                            .build()
                        recognizer?.recognizeAsync(
                            BitmapImageBuilder(bitmap).build(),
                            processingOptions,
                            frameTime,
                        )
                    } catch (t: Throwable) {
                        postError("Camera frame failed: ${t.message ?: "unknown error"}")
                    } finally {
                        image.close()
                    }
                }
                analysis = imageAnalysis
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis,
                )
                postStatus("Tracking active — frames remain on this device")
            } catch (t: Throwable) {
                postError("Could not bind camera: ${t.message ?: "camera unavailable"}")
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun handleResult(result: GestureRecognizerResult) {
        if (closed.get()) return
        val landmark = result.landmarks().firstOrNull()?.getOrNull(8)
        val raw = landmark?.let { NormalizedPoint(it.x(), it.y()) }
        val category = result.gestures().firstOrNull()?.firstOrNull()
        val label = category?.categoryName()
        val confidence = category?.score() ?: 0f
        postObservation(label, confidence)
        raw?.let { postPointer(mapToPreview(smoother.update(it), 1f, 1f)) }
        if (raw == null) smoother.reset()
    }

    private fun postStatus(text: String) = main.post {
        if (!closed.get()) listener.onStatus(text)
    }

    private fun postModelProgress(progress: ModelDownloadProgress) = main.post {
        if (!closed.get()) listener.onModelProgress(progress)
    }

    private fun postFrameInfo(rotation: Int, width: Int, height: Int) = main.post {
        if (!closed.get()) listener.onFrameInfo(rotation, width, height)
    }

    private fun postObservation(label: String?, confidence: Float) = main.post {
        if (!closed.get()) listener.onObservation(label, confidence)
    }

    private fun postPointer(point: NormalizedPoint) = main.post {
        if (!closed.get()) listener.onPointer(point)
    }

    private fun postError(text: String) = main.post {
        if (!closed.get()) listener.onError(text)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        main.post {
            runCatching { analysis?.clearAnalyzer() }
            runCatching { cameraProvider?.unbindAll() }
            analysis = null
            cameraProvider = null
        }
        executor.execute {
            runCatching { recognizer?.close() }
            recognizer = null
        }
        executor.shutdown()
        previewView = null
        modelBuffer = null
    }
}
