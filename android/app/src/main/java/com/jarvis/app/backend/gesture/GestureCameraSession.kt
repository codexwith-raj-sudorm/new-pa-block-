package com.jarvis.app.backend.gesture

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
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** Visible, activity-scoped CameraX + MediaPipe session. */
class GestureCameraSession(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val listener: Listener,
) {
    interface Listener {
        fun onStatus(text: String)
        fun onModelProgress(progress: ModelDownloadProgress)
        fun onModelReady(bytes: Long)
        fun onPointer(point: NormalizedPoint)
        fun onObservation(label: String?, confidence: Float)
        fun onAction(event: GestureEvent, screenPoint: ScreenPoint?)
        fun onError(text: String)
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "jarvis-gesture-camera").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val interpreter = GestureInterpreter()
    private var previewView: PreviewView? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var recognizer: GestureRecognizer? = null
    // MediaPipe requires a direct or mapped buffer for a model outside APK assets.
    // Keep the mapping alive for the whole recognizer lifetime.
    private var modelBuffer: MappedByteBuffer? = null
    private var lastTimestampMs = 0L

    fun start(view: PreviewView) {
        if (closed.get()) return
        previewView = view
        postStatus("Preparing local gesture recognition…")
        GestureModelRepository.ensureModel(
            context = appContext,
            executor = executor,
            onStatus = { text -> postStatus(text) },
            onProgress = { progress -> postModelProgress(progress) },
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
                val base = BaseOptions.builder().setModelAssetBuffer(mapped).build()
                val options = GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(base)
                    .setMinHandDetectionConfidence(0.65f)
                    .setMinHandPresenceConfidence(0.65f)
                    .setMinTrackingConfidence(0.65f)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result: GestureRecognizerResult, _ -> handleResult(result) }
                    .setErrorListener { error -> postError("Gesture recognition error: ${error.message}") }
                    .build()
                recognizer = GestureRecognizer.createFromOptions(appContext, options)
                main.post {
                    if (closed.get()) return@post
                    bindCamera(view)
                }
            } catch (t: Throwable) {
                postError("Could not start gesture recognition: ${t.message ?: "unknown error"}")
            }
        }
    }

    private fun bindCamera(view: PreviewView) {
        if (closed.get()) return
        try {
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener({
                try {
                    if (closed.get()) return@addListener
                    val provider = future.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }
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
                    postStatus("Gesture control active — camera processing stays on device")
                } catch (t: Throwable) {
                    postError("Could not bind camera: ${t.message ?: "camera unavailable"}")
                }
            }, ContextCompat.getMainExecutor(appContext))
        } catch (t: Throwable) {
            postError("Could not open camera: ${t.message ?: "camera unavailable"}")
        }
    }

    private fun handleResult(result: GestureRecognizerResult) {
        if (closed.get()) return
        val landmark = result.landmarks().firstOrNull()?.getOrNull(8)
        val point = landmark?.let { NormalizedPoint(it.x(), it.y()) }
        val category = result.gestures().firstOrNull()?.firstOrNull()
        val label = category?.categoryName()
        val confidence = category?.score() ?: 0f
        postObservation(label, confidence)
        interpreter.observe(point, label, confidence, SystemClock.uptimeMillis()).forEach { event ->
            when (event) {
                is GestureEvent.PointerMoved -> postPointer(event.point)
                is GestureEvent.Click, is GestureEvent.Confirm -> {
                    postAction(event, pointToScreen(eventPoint(event, point)))
                }
            }
        }
    }

    private fun eventPoint(event: GestureEvent, current: NormalizedPoint?): NormalizedPoint? = when (event) {
        is GestureEvent.PointerMoved -> event.point
        is GestureEvent.Click, is GestureEvent.Confirm -> current
    }

    private fun pointToScreen(point: NormalizedPoint?): ScreenPoint? {
        val view = previewView ?: return null
        if (point == null || view.width <= 0 || view.height <= 0) return null
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val local = mapToScreen(point, view.width.toFloat(), view.height.toFloat(), mirrorX = true)
        return ScreenPoint(location[0] + local.x, location[1] + local.y)
    }

    private fun postModelProgress(progress: ModelDownloadProgress) = main.post {
        if (!closed.get()) listener.onModelProgress(progress)
    }

    private fun postStatus(text: String) = main.post {
        if (!closed.get()) listener.onStatus(text)
    }

    private fun postError(text: String) = main.post {
        if (!closed.get()) listener.onError(text)
    }

    private fun postPointer(point: NormalizedPoint) = main.post {
        if (!closed.get()) listener.onPointer(point)
    }

    private fun postObservation(label: String?, confidence: Float) = main.post {
        if (!closed.get()) listener.onObservation(label, confidence)
    }

    private fun postAction(event: GestureEvent, point: ScreenPoint?) = main.post {
        if (!closed.get()) listener.onAction(event, point)
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
    }
}
