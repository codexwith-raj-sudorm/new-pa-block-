package com.jarvis.app.backend.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.ResultReceiver
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.jarvis.app.R

/** Consent cache: approve MediaProjection once, reuse until reboot/revoke. */
object ScreenConsent {
    var code: Int = 0
    var data: Intent? = null
}

/** Vision prompt for a screen question. Pure, tested. */
fun visionPromptFor(question: String): String {
    val q = question.lowercase()
    return if ("read" in q || "transcri" in q || "text" in q)
        "Transcribe all readable text on this phone screenshot, top to bottom. Be complete but concise."
    else
        "Describe what's on this phone screenshot in 2-3 short sentences. Lead with the app or content, then key details."
}

/** True for packages where screenshot-to-remote-AI should be refused. Pure, tested. */
fun screenVisionPackageBlocked(pkg: String?): Boolean {
    val p = pkg?.trim()?.lowercase().orEmpty()
    if (p.isBlank()) return false
    val exact = setOf(
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.authy.authy",
        "com.onepassword.android",
        "com.agilebits.onepassword",
        "com.lastpass.lpandroid",
        "com.dashlane",
        "com.x8bit.bitwarden",
        "net.one97.paytm",
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "com.google.android.apps.walletnfcrel",
        "in.org.npci.upiapp",
        "com.mobikwik_new",
        "com.dreamplug.androidapp",
        "com.paypal.android.p2pmobile"
    )
    if (p in exact) return true
    val tokens = listOf("password", "authenticator", "banking", ".bank.", "wallet", ".upi.")
    return tokens.any { it in p }
}

/** True when a watch-capture error means stale consent -> re-prompt once. Pure, tested. */
fun watchErrorNeedsReprompt(err: String): Boolean {
    val e = err.lowercase()
    if ("protected" in e || "blank" in e) return false
    return "consent" in e || "projection" in e || "permission" in e || "security" in e
}

/**
 * One-shot screenshot. Share mode saves a PNG and opens the share sheet;
 * watch mode saves a small JPEG and reports its path via the receiver.
 */
class ScreenshotService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            runCatching {
                nm.createNotificationChannel(
                    NotificationChannel("jarvis_shot", "Jarvis screenshots", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun shotNotif() = NotificationCompat.Builder(this, "jarvis_shot")
        .setSmallIcon(R.drawable.ic_stat_jarvis)
        .setContentTitle("Jarvis").setContentText("Capturing screenshot…").build()

    private fun parcelReceiver(intent: Intent?): ResultReceiver? = try {
        if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("receiver", ResultReceiver::class.java)
        else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("receiver")
        }
    } catch (_: Exception) {
        null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        }
        val mode = intent?.getStringExtra("mode") ?: "share"
        val receiver = parcelReceiver(intent)
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    7701, shotNotif(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(7701, shotNotif())
            }
        } catch (e: Exception) {
            receiver?.send(
                1, Bundle().apply {
                    putString("error", e.message ?: "couldn't start capture in background")
                }
            )
            stopSelf()
            return START_NOT_STICKY
        }
        Thread {
            try {
                if (code == 0 || data == null) throw IllegalStateException("no consent — approve the prompt")
                val bmp = grabFrame(code, data)
                try {
                    if (looksBlankOrProtected(bmp)) {
                        throw IllegalStateException("screen is blank or protected by Android security controls")
                    }
                    if (mode == "watch") {
                        val small = scaleDown(bmp, 768)
                        try {
                            val f = File(cacheDir, "watch.jpg")
                            FileOutputStream(f).use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                            receiver?.send(0, Bundle().apply { putString("path", f.absolutePath) })
                        } finally {
                            if (small !== bmp) small.recycle()
                        }
                    } else {
                        val f = File(cacheDir, "shot-" + System.currentTimeMillis() + ".png")
                        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                        val share = Intent(Intent.ACTION_SEND).setType("image/png")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(Intent.createChooser(share, "Share screenshot"))
                    }
                } finally {
                    bmp.recycle()
                }
            } catch (e: Exception) {
                val msg = e.message ?: "failed"
                if (watchErrorNeedsReprompt(msg)) {
                    ScreenConsent.code = 0
                    ScreenConsent.data = null
                }
                if (mode == "watch") {
                    receiver?.send(1, Bundle().apply { putString("error", msg) })
                } else {
                    Handler(mainLooper).post {
                        val text = if (msg.contains("protected", ignoreCase = true) || msg.contains("blank", ignoreCase = true)) {
                            "Protected screen can't be captured"
                        } else {
                            "Screenshot failed"
                        }
                        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun grabFrame(code: Int, data: Intent): Bitmap {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp: MediaProjection = mpm.getMediaProjection(code, data)
            ?: throw IllegalStateException("projection null — approve the prompt again")
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val vd = mp.createVirtualDisplay(
            "jarvis-shot", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
        )
        try {
            var bmp: Bitmap? = null
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 2500 && bmp == null) {
                Thread.sleep(250)
                val img = reader.acquireLatestImage()
                if (img != null) {
                    try {
                        val plane = img.planes[0]
                        val rowPad = plane.rowStride - plane.pixelStride * w
                        val full = Bitmap.createBitmap(
                            w + rowPad / plane.pixelStride, h, Bitmap.Config.ARGB_8888
                        )
                        full.copyPixelsFromBuffer(plane.buffer)
                        val cropped = Bitmap.createBitmap(full, 0, 0, w, h)
                        if (cropped !== full) full.recycle()
                        bmp = cropped
                    } finally {
                        img.close()
                    }
                }
            }
            return bmp ?: throw IllegalStateException("no frame — try again")
        } finally {
            try {
                vd.release()
            } catch (_: Exception) {
            }
            try {
                reader.close()
            } catch (_: Exception) {
            }
            try {
                mp.stop()
            } catch (_: Exception) {
            }
        }
    }

    /** FLAG_SECURE/protected windows commonly mirror as an all-black buffer. */
    private fun looksBlankOrProtected(b: Bitmap): Boolean {
        val w = b.width
        val h = b.height
        if (w <= 0 || h <= 0) return true
        val stepX = maxOf(1, w / 32)
        val stepY = maxOf(1, h / 32)
        var samples = 0
        var nearBlack = 0
        var lumaSum = 0L
        var y = stepY / 2
        while (y < h) {
            var x = stepX / 2
            while (x < w) {
                val c = b.getPixel(x, y)
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val blue = c and 0xff
                val luma = (r * 299 + g * 587 + blue * 114) / 1000
                if (luma <= 6) nearBlack++
                lumaSum += luma.toLong()
                samples++
                x += stepX
            }
            y += stepY
        }
        if (samples == 0) return true
        val avgLuma = lumaSum / samples
        return nearBlack * 1000 >= samples * 985 && avgLuma <= 8
    }

    private fun scaleDown(b: Bitmap, maxDim: Int): Bitmap {
        val s = maxDim / maxOf(b.width, b.height).toFloat()
        return if (s >= 1f) b
        else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }
}
