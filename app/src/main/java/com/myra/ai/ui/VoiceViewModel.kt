package com.myra.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myra.ai.MyraApplication
import com.myra.ai.ui.components.OrbState
import com.myra.ai.voice.VoiceListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VoiceViewModel(app: Application) : AndroidViewModel(app) {
    private val my = app as MyraApplication
    private val listener = VoiceListener(app)
    val state = MutableStateFlow(OrbState.Idle)
    val transcript = MutableStateFlow("")
    val reply = MutableStateFlow("")
    val info = MutableStateFlow<String?>(null)
    private var job: Job? = null

    fun toggle() { if (job?.isActive == true) stop() else start() }

    fun start() {
        if (!listener.available()) { info.value = "इस फ़ोन पर Speech Recognition उपलब्ध नहीं है।"; return }
        info.value = null
        job = viewModelScope.launch {
            while (isActive) {
                val s = my.settings.value
                state.value = OrbState.Listening
                transcript.value = ""
                val r = listener.listenOnce(s.listenLang) { transcript.value = it }
                val text = r.getOrNull()
                if (text == null) { info.value = "आवाज़ समझ नहीं आई। फिर से कोशिश करें।"; break }
                transcript.value = text
                state.value = OrbState.Thinking
                val rep = my.assistant.handle(text)
                reply.value = rep.text
                if (s.speakReplies) {
                    state.value = OrbState.Speaking
                    my.speaker.say(if (rep.isError) "माफ़ कीजिए, कुछ गड़बड़ हो गई।" else rep.text, s)
                }
                if (!s.continuous) break
            }
            state.value = OrbState.Idle
        }
    }

    fun stop() {
        job?.cancel()
        my.speaker.stop()
        state.value = OrbState.Idle
    }

    /** Stop speaking but keep the conversation loop alive. */
    fun interrupt() { my.speaker.stop() }

    override fun onCleared() { job?.cancel(); super.onCleared() }
}
