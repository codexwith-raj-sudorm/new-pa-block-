package com.jarvis.app.ui

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.ResultReceiver
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.jarvis.app.ScreenConsent
import com.jarvis.app.ScreenshotService

/** Transparent consent hop: asks once, caches the grant, hands off, finishes. */
class ShotActivity : ComponentActivity() {
    private fun receiver(): ResultReceiver? = try {
        if (Build.VERSION.SDK_INT >= 33) getIntent().getParcelableExtra("receiver", ResultReceiver::class.java)
        else {
            @Suppress("DEPRECATION") getIntent().getParcelableExtra("receiver")
        }
    } catch (_: Exception) {
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(7702)
        } catch (_: Exception) {
        }
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), 7717)
        } catch (_: Exception) {
            receiver()?.send(1, Bundle().apply { putString("error", "declined") })
            Toast.makeText(this, "Screen capture isn't available here", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("legacy result path")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7717 && resultCode == Activity.RESULT_OK && data != null) {
            ScreenConsent.code = resultCode
            ScreenConsent.data = data
            try {
                startForegroundService(
                    Intent(this, ScreenshotService::class.java)
                        .putExtra("code", resultCode).putExtra("data", data)
                        .putExtra("mode", getIntent().getStringExtra("mode") ?: "share")
                        .putExtra("receiver", receiver())
                )
            } catch (e: Exception) {
                receiver()?.send(1, Bundle().apply { putString("error", e.message ?: "couldn't start capture") })
                Toast.makeText(this, "Couldn't start capture", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 7717) {
            receiver()?.send(1, Bundle().apply { putString("error", "declined") })
            Toast.makeText(this, "Screen capture declined", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
