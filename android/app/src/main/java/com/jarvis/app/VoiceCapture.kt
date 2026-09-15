package com.jarvis.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Short-window 16kHz mono PCM capture for voiceprint verification/enrollment.
 * Fail-soft everywhere: any error yields false/null and the caller falls back
 * to the legacy (name/no-voiceprint) path.
 */
class VoiceCapture {
    @Volatile private var running = false
    private var recorder: AudioRecord? = null
    private var thread: Thread? = null
    private val chunks = mutableListOf<ShortArray>()
    private var total = 0
    private val maxSamples = VP_SAMPLE_RATE * 6 // 6s cap per capture

    /** Start capturing on a reader thread. False when the mic is unavailable. */
    fun start(): Boolean {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                VP_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return false
            val r = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, VP_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf * 2, VP_SAMPLE_RATE * 2)
            )
            if (r.state != AudioRecord.STATE_INITIALIZED) {
                try {
                    r.release()
                } catch (_: Exception) {
                }
                return false
            }
            r.startRecording()
            if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    r.stop()
                } catch (_: Exception) {
                }
                try {
                    r.release()
                } catch (_: Exception) {
                }
                return false
            }
            recorder = r
            chunks.clear()
            total = 0
            running = true
            thread = Thread({
                val buf = ShortArray(2048)
                while (running && total < maxSamples) {
                    val n = try {
                        r.read(buf, 0, buf.size)
                    } catch (_: Exception) {
                        break
                    }
                    if (n <= 0) {
                        if (!running) break
                        try {
                            Thread.sleep(10)
                        } catch (_: Exception) {
                            break
                        }
                        continue
                    }
                    val take = minOf(n, maxSamples - total)
                    synchronized(chunks) {
                        chunks.add(buf.copyOf(take))
                        total += take
                    }
                }
            }, "VoiceCapture").also { it.isDaemon = true; it.start() }
            true
        } catch (_: Exception) {
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
            recorder = null
            running = false
            false
        }
    }

    /** Stop and return all captured PCM (possibly empty). Never throws. */
    fun stop(): ShortArray? {
        running = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            thread?.join(500)
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        thread = null
        return try {
            synchronized(chunks) {
                val out = ShortArray(total)
                var o = 0
                for (c in chunks) {
                    c.copyInto(out, o)
                    o += c.size
                }
                out
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Blocking fixed-window record for enrollment. Null on any failure. */
        fun recordFixedMs(ms: Int): ShortArray? {
            var r: AudioRecord? = null
            return try {
                val minBuf = AudioRecord.getMinBufferSize(
                    VP_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) return null
                r = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, VP_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf * 2, VP_SAMPLE_RATE * 2)
                )
                if (r.state != AudioRecord.STATE_INITIALIZED) {
                    try {
                        r.release()
                    } catch (_: Exception) {
                    }
                    return null
                }
                r.startRecording()
                if (r.recordingState != AudioRecord.RECORDSTATE_RECORDING) return null
                val want = VP_SAMPLE_RATE * ms / 1000
                val out = ShortArray(want)
                var o = 0
                val buf = ShortArray(2048)
                val deadline = System.currentTimeMillis() + ms + 1500
                while (o < want && System.currentTimeMillis() < deadline) {
                    val n = try {
                        r.read(buf, 0, buf.size)
                    } catch (_: Exception) {
                        break
                    }
                    if (n <= 0) {
                        try {
                            Thread.sleep(10)
                        } catch (_: Exception) {
                            break
                        }
                        continue
                    }
                    val take = minOf(n, want - o)
                    buf.copyInto(out, o, 0, take)
                    o += take
                }
                if (o < want / 2) null else out.copyOf(o)
            } catch (_: Exception) {
                null
            } finally {
                try {
                    r?.stop()
                } catch (_: Exception) {
                }
                try {
                    r?.release()
                } catch (_: Exception) {
                }
            }
        }
    }
}
