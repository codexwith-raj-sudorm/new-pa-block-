package com.jarvis.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.jarvis.app.ScreenshotService

/** Transparent consent hop for screen capture: asks once, hands off, finishes. */
class ShotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), 7717)
        } catch (_: Exception) {
            Toast.makeText(this, "Screen capture isn't available here", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("legacy result path")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7717 && resultCode == Activity.RESULT_OK && data != null) {
            try {
                startForegroundService(
                    Intent(this, ScreenshotService::class.java)
                        .putExtra("code", resultCode).putExtra("data", data)
                )
            } catch (_: Exception) {
                Toast.makeText(this, "Couldn't start capture", Toast.LENGTH_SHORT).show()
            }
        } else if (requestCode == 7717) {
            Toast.makeText(this, "Screen capture declined", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
