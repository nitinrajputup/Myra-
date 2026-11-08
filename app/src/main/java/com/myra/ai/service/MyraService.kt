package com.myra.ai.service

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.myra.ai.MainActivity
import com.myra.ai.MyraApplication
import com.myra.ai.automation.Automation
import com.myra.ai.core.CommandRouter
import com.myra.ai.device.BatteryInfo
import com.myra.ai.voice.SpeechError
import com.myra.ai.voice.VoiceListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Foreground service (visible notification) that hosts:
 *  - battery announcements (real BATTERY_CHANGED events)
 *  - optional wake-word listening using SpeechRecognizer in a loop
 */
class MyraService : Service() {
    companion object {
        val running = MutableStateFlow(false)
        const val ACTION_STOP = "com.myra.ai.STOP"
        private const val NID = 42

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MyraService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MyraService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: MyraApplication
    private var wakeJob: Job? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastPlugged: Boolean? = null
    private var lowDone = false
    private var fullDone = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        app = application as MyraApplication
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Automation.log("Service not started: microphone permission missing")
            stopSelf()
            return START_NOT_STICKY
        }
        startFg()
        running.value = true
        startBattery()
        scope.launch {
            app.settings.map { it.wakeEnabled }.distinctUntilChanged().collect { on ->
                wakeJob?.cancel()
                wakeJob = if (on) launch { wakeLoop() } else null
            }
        }
        return START_STICKY
    }

    private fun startFg() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, MyraService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, "service")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("MYRA सक्रिय है")
            .setContentText("बैटरी घोषणाएँ / वेक वर्ड (माइक उपयोग में हो सकता है)")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NID, n)
        }
    }

    private fun startBattery() {
        if (batteryReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                val b = BatteryInfo.from(i)
                val s = app.settings.value
                val prev = lastPlugged
                lastPlugged = b.charging
                if (prev == false && b.charging && s.chargeAnn) announce(s.msgCharging)
                if (!b.charging && b.percent <= s.lowThreshold && !lowDone) {
                    lowDone = true
                    if (s.lowAnn) announce(s.msgLow)
                }
                if (b.charging || b.percent > s.lowThreshold + 3) lowDone = false
                if (b.charging && b.percent >= 100 && !fullDone) {
                    fullDone = true
                    if (s.fullAnn) announce(s.msgFull)
                }
                if (!b.charging) fullDone = false
            }
        }
        batteryReceiver = r
        ContextCompat.registerReceiver(this, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun announce(msg: String) {
        scope.launch { app.speaker.say(msg, app.settings.value) }
    }

    private suspend fun wakeLoop() {
        val vl = VoiceListener(this)
        if (!vl.available()) {
            Automation.log("Speech recognition unavailable on this device")
            return
        }
        var fails = 0
        while (currentCoroutineContext().isActive) {
            val s = app.settings.value
            val r = vl.listenOnce(s.listenLang)
            val text = r.getOrNull()
            if (text == null) {
                val code = (r.exceptionOrNull() as? SpeechError)?.code ?: -1
                if (code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    Automation.log("Mic permission missing — wake word stopped")
                    return
                }
                fails++
                delay(if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || fails > 5) 2000L else 400L)
                continue
            }
            fails = 0
            val hit = CommandRouter.findWake(text) ?: continue
            if (hit.rest.isNotBlank()) {
                respond(hit.rest)
            } else {
                app.speaker.say(if (hit.greeted) s.replyHello else s.replyMyra, s)
                val cmd = vl.listenOnce(s.listenLang).getOrNull()
                if (!cmd.isNullOrBlank()) respond(cmd)
            }
        }
    }

    private suspend fun respond(cmd: String) {
        val s = app.settings.value
        val rep = app.assistant.handle(cmd)
        if (s.speakReplies) app.speaker.say(if (rep.isError) "माफ़ कीजिए, कुछ गड़बड़ हो गई।" else rep.text, s)
    }

    override fun onDestroy() {
        running.value = false
        batteryReceiver?.let { unregisterReceiver(it) }
        batteryReceiver = null
        scope.cancel()
        super.onDestroy()
    }
}
