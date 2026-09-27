package com.jarvis.app.frontend.screens

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jarvis.app.backend.system.sharedJarvisVm
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

/** Shared-text preview for the Stark Hub (blank/null -> fallback). Pure, tested. */
fun starkSharedPreview(text: String?): String =
    text?.take(4000).orEmpty().ifBlank { "No text payload detected." }

/** Vision question for a shared image. Pure, tested. */
fun sharedImageQuestion(readText: Boolean): String =
    if (readText) "Read all text in this image, top to bottom."
    else "What's in this image?"

class StarkShareActivity : ComponentActivity() {
    companion object {
        const val EXTRA_AUTO_SEND = "com.jarvis.app.AUTO_SEND"
    }

    private fun sharedStream(): Uri? = try {
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    } catch (_: Exception) {
        null
    }

    /** Downscaled JPEG base64 for vision (shared image, max 768px). Null on failure. */
    private fun imageB64ForVision(uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 768) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, baos)
        Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = sharedJarvisVm(application as Application)
        val sharedText = starkSharedPreview(intent.getStringExtra(Intent.EXTRA_TEXT))
        val imageUri = sharedStream()

        setContent {
            var result by remember { mutableStateOf<String?>(null) }
            var working by remember { mutableStateOf(false) }
            fun runShareJob(job: suspend () -> String) {
                if (working) return
                working = true
                result = null
                lifecycleScope.launch {
                    try {
                        result = job()
                    } catch (e: Exception) {
                        result = "Failed: ${e.message?.take(120)}"
                    } finally {
                        working = false
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF0B1220)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "STARK HUB // SHARE INTERCEPT",
                        color = Color(0xFFFBBF24),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (imageUri != null) {
                        ResultBox("Shared image ready for vision.", Color(0xFFF59E0B))
                    } else {
                        ResultBox(sharedText, Color(0xFFF59E0B))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (working) {
                        CircularProgressIndicator(color = Color(0xFFFBBF24))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (result != null) {
                        ResultBox(result!!, Color(0xFF22D3EE))
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    if (imageUri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShareButton("DESCRIBE") {
                                runShareJob {
                                    val b64 = imageB64ForVision(imageUri!!)
                                        ?: return@runShareJob "Couldn't read that image."
                                    vm.describeSharedImage(b64, sharedImageQuestion(false))
                                }
                            }
                            ShareButton("READ TEXT") {
                                runShareJob {
                                    val b64 = imageB64ForVision(imageUri!!)
                                        ?: return@runShareJob "Couldn't read that image."
                                    vm.describeSharedImage(b64, sharedImageQuestion(true))
                                }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ShareButton("SUMMARIZE") { runShareJob { vm.summarizeShared(sharedText) } }
                            ShareButton("PROCESS & INJECT") { injectToJarvis(sharedText); finish() }
                        }
                    }
                    if (result != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ShareButton("INJECT RESULT TO JARVIS") { injectToJarvis(result!!); finish() }
                    }
                }
            }
        }
    }

    private fun injectToJarvis(text: String) {
        try {
            val i = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .putExtra(EXTRA_AUTO_SEND, true)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(i)
        } catch (_: Exception) {
        }
    }
}

@androidx.compose.runtime.Composable
private fun ResultBox(text: String, edge: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), shape = RoundedCornerShape(8.dp))
            .border(1.dp, edge.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(text = text, color = Color(0xFFE2E8F0), fontSize = 14.sp)
    }
}

@androidx.compose.runtime.Composable
private fun ShareButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
    ) {
        Text(label, color = Color.White, fontFamily = FontFamily.Monospace)
    }
}
