package com.calmdrive.visualtimer.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class SpokenAlertEngine(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setSpeechRate(0.92f) // Calmer, slightly slower cadence for neurodivergent kids
            tts?.setPitch(1.02f)
            isReady = true
        }
    }

    fun speak(text: String) {
        if (!isReady || tts == null) return
        try {
            // Strip any emoji characters so TTS speaks natural words
            val clean = text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Z}]"), "").trim()
            if (clean.isNotEmpty()) {
                tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "calmdrive_alert")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isReady = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
