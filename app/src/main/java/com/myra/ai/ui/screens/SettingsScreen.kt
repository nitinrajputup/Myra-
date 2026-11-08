package com.myra.ai.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.myra.ai.MyraApplication
import com.myra.ai.data.MyraSettings
import com.myra.ai.service.MyraService
import com.myra.ai.ui.MainViewModel
import com.myra.ai.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as MyraApplication
    val scope = rememberCoroutineScope()
    val saved by vm.settings.collectAsState()
    val test by vm.testResult.collectAsState()
    val svc by MyraService.running.collectAsState()
    var d by remember(saved) { mutableStateOf(saved) }
    var tick by remember { mutableStateOf(0) }
    var savedNote by remember { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    val micOk = remember(tick) { hasPerm(ctx, Manifest.permission.RECORD_AUDIO) }
    val notifOk = remember(tick) { Build.VERSION.SDK_INT < 33 || hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val a11yOk = remember(tick) { a11yEnabled(ctx) }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val micReq = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val micThenSvc = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> tick++; if (ok) MyraService.start(ctx) }
    val notifReq = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val voices = remember(tick) { listOf("(default)") + app.speaker.voices() }

    fun upd(n: MyraSettings) { d = n }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", fontSize = 22.sp, color = Color.White, modifier = Modifier.weight(1f))
            Button(onClick = { vm.save(d); savedNote = true }) { Text("Save") }
        }
        if (savedNote) Text("✅ सेव हो गया", color = Color(0xFF9EE493), fontSize = 12.sp)

        Glass {
            Title("AI (Gemini)")
            OutlinedTextField(d.apiKey, { upd(d.copy(apiKey = it)) }, Modifier.fillMaxWidth(), label = { Text("Gemini API Key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(d.model, { upd(d.copy(model = it)) }, Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
            Pick("Preset", d.model, listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite")) { upd(d.copy(model = it)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.test(d) }) { Text("Test connection") }
            }
            test?.let { Text(it, color = Color.White, fontSize = 12.sp) }
            Pick("Personality", d.personality, listOf("Friendly", "Professional", "Playful", "Calm")) { upd(d.copy(personality = it)) }
            Pick("Style", d.responseStyle, listOf("Concise", "Detailed", "Step-by-step")) { upd(d.copy(responseStyle = it)) }
            Pick("Reply language", d.appLanguage, listOf("Auto", "Hindi", "English", "Hinglish")) { upd(d.copy(appLanguage = it)) }
            OutlinedTextField(d.instructions, { upd(d.copy(instructions = it)) }, Modifier.fillMaxWidth(), label = { Text("Custom instructions") }, minLines = 3)
            SwitchRow("Saved memories इस्तेमाल करें", d.memoryEnabled, { upd(d.copy(memoryEnabled = it)) })
            SwitchRow("Web search (Google grounding)", d.webSearch, { upd(d.copy(webSearch = it)) }, "सभी models/keys पर उपलब्ध न भी हो; error आने पर बंद करें")
            Text("Key एन्क्रिप्ट होकर (Android Keystore) सिर्फ़ इस फ़ोन में रहती है। Streaming अभी नहीं है — पूरा उत्तर एक साथ आता है।", color = Color.Gray, fontSize = 11.sp)
        }

        Glass {
            Title("Voice")
            Pick("Voice", d.voiceName.ifBlank { "(default)" }, voices) { upd(d.copy(voiceName = if (it == "(default)") "" else it)) }
            Text("आवाज़ का gender फ़ोन के TTS engine (जैसे Google TTS, hi-IN) पर निर्भर है — यहाँ से अपनी पसंद की आवाज़ चुनकर Test करें।", color = Color.Gray, fontSize = 11.sp)
            Pick("Listen language", d.listenLang, listOf("hi-IN", "en-IN", "en-US")) { upd(d.copy(listenLang = it)) }
            SliderRow("Speed", d.voiceSpeed, 0.5f..2f) { upd(d.copy(voiceSpeed = it)) }
            SliderRow("Pitch", d.voicePitch, 0.5f..2f) { upd(d.copy(voicePitch = it)) }
            SliderRow("Volume", d.voiceVolume, 0.1f..1f) { upd(d.copy(voiceVolume = it)) }
            OutlinedButton(onClick = { scope.launch { app.speaker.say("नमस्ते, मैं MYRA हूँ। आपकी क्या मदद करूँ?", d) } }) { Text("🔊 Test voice") }
            SwitchRow("जवाब बोलकर सुनाएँ", d.speakReplies, { upd(d.copy(speakReplies = it)) })
            SwitchRow("Continuous conversation (Voice screen)", d.continuous, { upd(d.copy(continuous = it)) })
            SwitchRow("Interrupt speaking", d.allowInterrupt, { upd(d.copy(allowInterrupt = it)) })
            Divider()
            SwitchRow("Wake word (Hello/Hey Myra, Myra)", d.wakeEnabled, { upd(d.copy(wakeEnabled = it)) },
                "Background service चालू होना चाहिए। लगातार माइक उपयोग होता है, बैटरी ज़्यादा लगती है, और कुछ फ़ोन बीच में रोक सकते हैं।")
            OutlinedTextField(d.replyHello, { upd(d.copy(replyHello = it)) }, Modifier.fillMaxWidth(), label = { Text("\"Hello Myra\" का जवाब") })
            OutlinedTextField(d.replyMyra, { upd(d.copy(replyMyra = it)) }, Modifier.fillMaxWidth(), label = { Text("\"Myra\" का जवाब") })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (svc) "✅ Service चालू" else "⚪ Service बंद", color = Color.White, modifier = Modifier.weight(1f))
                if (!svc) Button(onClick = {
                    vm.save(d)
                    if (micOk) MyraService.start(ctx) else micThenSvc.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Start") }
                else OutlinedButton(onClick = { MyraService.stop(ctx) }) { Text("Stop") }
            }
        }

        Glass {
            Title("Permissions & Automation")
            Text(if (micOk) "✅ Microphone" else "⚪ Microphone", color = Color.White)
            if (!micOk) Button(onClick = { micReq.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Grant microphone") }
            Text(if (notifOk) "✅ Notifications" else "⚪ Notifications (reminders के लिए)", color = Color.White)
            if (!notifOk && Build.VERSION.SDK_INT >= 33) Button(onClick = { notifReq.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Grant notifications") }
            Text(if (a11yOk) "✅ Accessibility" else "⚪ Accessibility (हाथ से चालू करें)", color = Color.White)
            if (!a11yOk) Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open Accessibility settings") }
            Text(if (overlayOk) "✅ Display over other apps" else "⚪ Display over other apps (background से ऐप खोलने के लिए)", color = Color.White)
            if (!overlayOk) Button(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Grant overlay") }
            SwitchRow("संवेदनशील क्रियाओं पर पुष्टि माँगें", d.confirmSensitive, { upd(d.copy(confirmSensitive = it)) })
            OutlinedTextField(d.ytKey, { upd(d.copy(ytKey = it)) }, Modifier.fillMaxWidth(), label = { Text("YouTube Data API key (optional)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation())
        }

        Glass {
            Title("Battery announcements")
            SwitchRow("Charging लगने पर बोलो", d.chargeAnn, { upd(d.copy(chargeAnn = it)) })
            SwitchRow("Battery low पर बोलो", d.lowAnn, { upd(d.copy(lowAnn = it)) })
            SwitchRow("Battery full पर बोलो", d.fullAnn, { upd(d.copy(fullAnn = it)) })
            SliderRow("Low threshold %", d.lowThreshold.toFloat(), 5f..50f) { upd(d.copy(lowThreshold = it.toInt())) }
            OutlinedTextField(d.msgCharging, { upd(d.copy(msgCharging = it)) }, Modifier.fillMaxWidth(), label = { Text("Charging message") })
            OutlinedTextField(d.msgLow, { upd(d.copy(msgLow = it)) }, Modifier.fillMaxWidth(), label = { Text("Low message") })
            OutlinedTextField(d.msgFull, { upd(d.copy(msgFull = it)) }, Modifier.fillMaxWidth(), label = { Text("Full message") })
            Text("घोषणाएँ तभी आती हैं जब Background service चालू हो (असली BATTERY_CHANGED events से)।", color = Color.Gray, fontSize = 11.sp)
        }

        Glass {
            Title("General")
            Pick("Accent", d.accent, listOf("Purple", "Blue", "Cyan", "Pink")) { upd(d.copy(accent = it)) }
            Text("Privacy: चैट और memory सिर्फ़ इस फ़ोन में (Room DB) रहती हैं। Gemini को सिर्फ़ वही टेक्स्ट/फ़ाइल जाती है जो आप भेजते हैं (और चालू होने पर saved memories)।", color = Color.Gray, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.exportData() }) { Text("Export data") }
                OutlinedButton(onClick = { vm.clearHistory() }) { Text("Clear history") }
            }
            OutlinedButton(onClick = { vm.clearMemories() }) { Text("Delete memory") }
            Text("MYRA AI v1.0.0", color = Color.Gray, fontSize = 11.sp)
        }
    }
}
