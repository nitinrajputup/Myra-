package com.myra.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class SpeechError(val code: Int) : Exception("speech error $code")

/** Must be called from the main thread (SpeechRecognizer requirement). */
class VoiceListener(private val ctx: Context) {
    fun available() = SpeechRecognizer.isRecognitionAvailable(ctx)

    suspend fun listenOnce(lang: String, onPartial: (String) -> Unit = {}): Result<String> =
        suspendCancellableCoroutine { cont ->
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(b: Bundle?) {
                    b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
                }
                override fun onResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    r.destroy()
                    if (cont.isActive) {
                        cont.resume(if (t.isBlank()) Result.failure(SpeechError(-1)) else Result.success(t))
                    }
                }
                override fun onError(error: Int) {
                    r.destroy()
                    if (cont.isActive) cont.resume(Result.failure(SpeechError(error)))
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            r.startListening(i)
            cont.invokeOnCancellation { Handler(Looper.getMainLooper()).post { r.destroy() } }
        }
}
