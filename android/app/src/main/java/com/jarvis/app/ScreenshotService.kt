package com.jarvis.app

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
import android.os.Handler
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** One-shot screenshot: captures a frame, saves to cache, opens the share sheet. */
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
        val notif = NotificationCompat.Builder(this, "jarvis_shot")
            .setSmallIcon(R.drawable.ic_stat_jarvis)
            .setContentTitle("Jarvis").setContentText("Capturing screenshot…").build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                7701, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(7701, notif)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("data")
        }
        Thread {
            try {
                if (code == 0 || data == null) throw IllegalStateException("no consent")
                capture(code, data)
            } catch (_: Exception) {
                Handler(mainLooper).post {
                    Toast.makeText(this, "Screenshot failed", Toast.LENGTH_SHORT).show()
                }
            } finally {
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun capture(code: Int, data: Intent) {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp: MediaProjection = mpm.getMediaProjection(code, data)
            ?: throw IllegalStateException("projection null")
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
                        bmp = Bitmap.createBitmap(full, 0, 0, w, h)
                    } finally {
                        img.close()
                    }
                }
            }
            val b = bmp ?: throw IllegalStateException("no frame")
            val f = File(cacheDir, "shot-" + System.currentTimeMillis() + ".png")
            FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val share = Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Share screenshot"))
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
}
