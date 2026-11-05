package com.calmdrive.visualtimer.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

object HarmonicChimePlayer {

    private const val SAMPLE_RATE = 44100
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Plays a non-startling, gentle marimba/singing-bowl chime for sensory sensitivity.
     */
    fun playChime(isDone: Boolean) {
        scope.launch {
            try {
                // Frequencies: Ascending celebration pentatonic (C5, E5, G5, C6) or warm triad (A4, C#5, E5)
                val freqs = if (isDone) {
                    floatArrayOf(523.25f, 659.25f, 783.99f, 1046.5f)
                } else {
                    floatArrayOf(440.0f, 554.37f, 659.25f)
                }

                val durationSec = 1.3f
                val totalSamples = (SAMPLE_RATE * durationSec).toInt()
                val buffer = ShortArray(totalSamples)

                val noteIntervalSamples = (SAMPLE_RATE * 0.14f).toInt()

                for (n in freqs.indices) {
                    val freq = freqs[n]
                    val startSample = n * noteIntervalSamples
                    val noteLen = totalSamples - startSample

                    for (i in 0 until noteLen) {
                        val sampleIdx = startSample + i
                        if (sampleIdx >= totalSamples) break

                        val t = i.toFloat() / SAMPLE_RATE.toFloat()
                        // Gentle exponential decay envelope (fast 30ms attack, smooth release)
                        val attack = (t / 0.035f).coerceAtMost(1f)
                        val decay = exp(-3.2 * t).toFloat()
                        val envelope = attack * decay * 0.28f

                        val sampleValue = (sin(2.0 * Math.PI * freq * t) * envelope * Short.MAX_VALUE).toInt()
                        val current = buffer[sampleIdx].toInt()
                        val mixed = (current + sampleValue).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[sampleIdx] = mixed.toShort()
                    }
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()

                // Release track after playback completes
                kotlinx.coroutines.delay((durationSec * 1000).toLong() + 200)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
