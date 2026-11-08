package com.myra.ai.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.myra.ai.data.MyraSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

class Speaker(ctx: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private var cfg = MyraSettings()
    val speaking = MutableStateFlow(false)

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                tts?.language = Locale("hi", "IN")
                apply(cfg)
            }
        }
    }

    /** Voices installed on this device (Hindi/English). Gender depends on the installed TTS engine. */
    fun voices(): List<String> = try {
        tts?.voices?.filter { it.locale.language == "hi" || it.locale.language == "en" }
            ?.map { it.name }?.sorted() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun apply(s: MyraSettings) {
        cfg = s
        val t = tts ?: return
        if (!ready) return
        try {
            t.setSpeechRate(s.voiceSpeed)
            t.setPitch(s.voicePitch)
            if (s.voiceName.isNotBlank()) {
                t.voices?.firstOrNull { it.name == s.voiceName }?.let { t.voice = it }
            }
        } catch (_: Exception) {
        }
    }

    suspend fun say(text: String, s: MyraSettings = cfg) {
        val t = tts
        val clean = text.replace(Regex("[*#`_>]+"), " ").take(3500)
        if (!ready || t == null || clean.isBlank()) return
        apply(s)
        suspendCancellableCoroutine<Unit> { cont ->
            val id = UUID.randomUUID().toString()
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { speaking.value = true }
                override fun onDone(utteranceId: String?) {
                    speaking.value = false
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onError(utteranceId: String?) {
                    speaking.value = false
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    speaking.value = false
                    if (cont.isActive) cont.resume(Unit)
                }
            })
            val p = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, s.voiceVolume) }
            if (t.speak(clean, TextToSpeech.QUEUE_FLUSH, p, id) != TextToSpeech.SUCCESS) {
                if (cont.isActive) cont.resume(Unit)
            }
            cont.invokeOnCancellation { t.stop() }
        }
    }

    fun stop() {
        tts?.stop()
        speaking.value = false
    }
}
