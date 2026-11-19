package com.standup.app.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.standup.app.data.AppState
import com.standup.app.data.SoundSource
import com.standup.app.data.Store

/** Plays the configured sound and/or speaks the message. Used by the service and the preview button. */
class ReminderAudio(context: Context) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeak: String? = null
    private var onSpeechDone: (() -> Unit)? = null

    /** Follows the user's chosen volume channel (Settings > Volume). */
    private fun attrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(Store.state.value.audioChannel.usage)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val defaultUri: Uri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    fun uriFor(s: AppState): Uri = when (s.soundSource) {
        SoundSource.CUSTOM -> if (s.customUri.isNotBlank()) Uri.parse(s.customUri) else defaultUri
        else -> if (s.builtinUri.isNotBlank()) Uri.parse(s.builtinUri) else defaultUri
    }

    /**
     * One cycle: sound first, then speech (if enabled) once the sound has finished.
     * [onComplete] is called when everything has finished playing (not called if stopped early).
     */
    fun playConfigured(s: AppState, onComplete: () -> Unit = {}) {
        val speakNow: () -> Unit = {
            if (s.speakEnabled && s.speakText.isNotBlank()) speak(s.speakText, onComplete) else onComplete()
        }
        if (s.soundSource == SoundSource.SILENT) {
            stopSound()
            speakNow()
        } else {
            playSound(uriFor(s), speakNow)
        }
    }

    fun playSound(uri: Uri, onDone: () -> Unit = {}) = start(uri, true, onDone)

    private fun start(uri: Uri, allowFallback: Boolean, onDone: () -> Unit) {
        stopSound()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(attrs())
            mp.setDataSource(ctx, uri)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { finish(it); onDone() }
            mp.setOnErrorListener { m, _, _ ->
                finish(m)
                if (allowFallback) start(defaultUri, false, onDone) else onDone()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            // Missing/unreadable custom file etc.: fall back to the default sound once.
            finish(mp)
            if (allowFallback) start(defaultUri, false, onDone) else onDone()
        }
    }

    private fun finish(mp: MediaPlayer) {
        runCatching { mp.release() }
        if (player === mp) player = null
    }

    private fun initTts(): TextToSpeech = TextToSpeech(ctx) { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts?.setAudioAttributes(attrs())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { main.post { fireSpeechDone() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { fireSpeechDone() } }
            })
            ttsReady = true
            pendingSpeak?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "standup") }
            pendingSpeak = null
        } else {
            main.post { fireSpeechDone() } // no TTS engine: do not block completion
        }
    }

    private fun fireSpeechDone() {
        val cb = onSpeechDone
        onSpeechDone = null
        cb?.invoke()
    }

    fun speak(text: String, onDone: () -> Unit = {}) {
        onSpeechDone = onDone
        val t = tts
        when {
            t == null -> {
                pendingSpeak = text
                tts = initTts()
            }
            ttsReady -> {
                t.setAudioAttributes(attrs()) // channel may have changed since init
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "standup")
            }
            else -> pendingSpeak = text
        }
    }

    fun stopSound() {
        player?.let { finish(it) }
        player = null
    }

    /** Immediately silence everything. */
    fun stop() {
        stopSound()
        pendingSpeak = null
        onSpeechDone = null
        tts?.stop()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }
}
