package com.calmdrive.visualtimer.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class SilentKeepAlivePlayer {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false

    fun start() {
        if (isPlaying) return
        try {
            val sampleRate = 8000
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(1024)

            val silenceBuffer = ShortArray(bufferSize)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(silenceBuffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isPlaying = true

            // Write silence in a small background thread or write once
            Thread {
                while (isPlaying) {
                    audioTrack?.write(silenceBuffer, 0, silenceBuffer.size)
                    try {
                        Thread.sleep(100)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        if (!isPlaying) return
        isPlaying = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        isPlaying = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
