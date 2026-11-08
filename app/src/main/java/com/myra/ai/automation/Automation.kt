package com.myra.ai.automation

import com.myra.ai.MyraApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Automation {
    data class Pending(val description: String, val run: () -> String)

    val logs = MutableStateFlow<List<String>>(emptyList())
    val status = MutableStateFlow("Idle")
    val pending = MutableStateFlow<Pending?>(null)
    @Volatile var stopped = false

    fun log(msg: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs.update { (it + "$t  $msg").takeLast(200) }
    }

    fun emergencyStop(app: MyraApplication) {
        stopped = true
        pending.value = null
        status.value = "Stopped"
        app.speaker.stop()
        log("EMERGENCY STOP")
    }

    fun resume() {
        stopped = false
        status.value = "Idle"
        log("Resumed")
    }
}
