package com.myra.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.content.BroadcastReceiver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.myra.ai.automation.MyraAccessibilityService
import com.myra.ai.core.Agent
import com.myra.ai.device.BatteryInfo
import com.myra.ai.service.MyraService
import com.myra.ai.ui.MainViewModel
import com.myra.ai.ui.VoiceViewModel
import com.myra.ai.ui.components.*
import com.myra.ai.ui.theme.MyraCard

fun hasPerm(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

fun a11yEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.contains(ctx.packageName) && s.contains("MyraAccessibilityService")
}

@Composable
fun HomeScreen(vm: MainViewModel, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val loading by vm.loading.collectAsState()
    val s by vm.settings.collectAsState()
    val svc by MyraService.running.collectAsState()
    var bat by remember { mutableStateOf(BatteryInfo.read(ctx)) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { bat = BatteryInfo.from(i) }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        MyraOrb(if (loading) OrbState.Thinking else OrbState.Idle)
        Text("नमस्ते, मैं MYRA हूँ।\nआपकी क्या मदद करूँ?", fontSize = 22.sp, textAlign = TextAlign.Center, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { go("voice") }) { Text("🎤 बोलिए") }
            OutlinedButton(onClick = { go("chat") }) { Text("Chat") }
        }
        Glass {
            Title("Status")
            Text("🔋 बैटरी ${bat.percent}%" + if (bat.charging) " (चार्जिंग)" else "", color = Color.White)
            Text(if (s.apiKey.isBlank()) "⚠️ Gemini key नहीं है — Settings खोलें" else "✅ Gemini: ${s.model}", color = Color.White)
            Text(if (a11yEnabled(ctx)) "✅ Accessibility चालू" else "⚪ Accessibility बंद (Automation के लिए)", color = Color.White)
            Text(if (svc) "✅ Background service चालू" else "⚪ Background service बंद", color = Color.White)
        }
    }
}

@Composable
fun VoiceScreen(vm: VoiceViewModel) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val tr by vm.transcript.collectAsState()
    val rep by vm.reply.collectAsState()
    val info by vm.info.collectAsState()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.start() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        MyraOrb(state, 240.dp)
        Text(
            when (state) {
                OrbState.Idle -> "माइक दबाकर बोलिए"
                OrbState.Listening -> "सुन रही हूँ…"
                OrbState.Thinking -> "सोच रही हूँ…"
                OrbState.Speaking -> "बोल रही हूँ…"
            }, color = Color.Gray
        )
        if (tr.isNotBlank()) Glass { Text("आप: $tr", color = Color.White) }
        if (rep.isNotBlank()) Glass { SelectionContainer { Text("MYRA: $rep", color = Color.White) } }
        info?.let { Text(it, color = Color(0xFFFF6B6B)) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    if (state != OrbState.Idle) vm.stop()
                    else if (hasPerm(ctx, Manifest.permission.RECORD_AUDIO)) vm.start()
                    else perm.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.size(84.dp), shape = RoundedCornerShape(50)
            ) { Text(if (state == OrbState.Idle) "🎤" else "■", fontSize = 28.sp) }
            if (state == OrbState.Speaking) OutlinedButton(onClick = { vm.interrupt() }) { Text("चुप कराओ") }
        }
        Text(
            "ऐप खुला होने पर यह माइक बटन काम करता है। \"Hello Myra\" वेक वर्ड के लिए Settings → Voice में Background service चालू करें " +
                "(Android इसके लिए स्थायी notification दिखाता है और माइक उपयोग में रहता है).",
            color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChatScreen(vm: MainViewModel) {
    val msgs by vm.messages.collectAsState()
    val loading by vm.loading.collectAsState()
    val err by vm.error.collectAsState()
    val agent by vm.agent.collectAsState()
    val att by vm.attach.collectAsState()
    val prompts by vm.prompts.collectAsState()
    var input by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var showPrompts by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.attachUri(it) } }
    val list = rememberLazyListState()
    val shown = if (query.isBlank()) msgs else msgs.filter { it.text.contains(query, true) }
    LaunchedEffect(msgs.size, loading) { if (shown.isNotEmpty()) list.animateScrollToItem(shown.size) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(Agent.values().toList()) { a -> Chip(a.label, a == agent) { vm.agent.value = a } }
            }
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Default.Search, "search") }
            IconButton(onClick = { vm.newChat() }) { Icon(Icons.Default.Add, "new chat") }
        }
        if (searching) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("इस चैट में खोजें") }, singleLine = true)
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.id }) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                    SelectionContainer {
                        Column(
                            Modifier.background(
                                if (m.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MyraCard,
                                RoundedCornerShape(16.dp)
                            ).padding(12.dp).widthIn(max = 310.dp)
                        ) {
                            m.attachName?.let { Text("📎 $it", color = Color.LightGray, fontSize = 11.sp) }
                            Text(m.text, color = Color.White)
                        }
                    }
                }
            }
            if (loading) item { Text("MYRA सोच रही है…", color = Color.Gray) }
            err?.let { e -> item { Text(e, color = Color(0xFFFF6B6B)) } }
        }
        att?.let { Text("📎 ${it.name}  (भेजते समय जुड़ेगा)", color = Color.LightGray, fontSize = 12.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Default.Add, "attach") }
            IconButton(onClick = { showPrompts = true }) { Icon(Icons.Default.Star, "prompts") }
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("MYRA से पूछें…") }, maxLines = 4)
            IconButton(onClick = { vm.send(input); input = "" }, enabled = !loading) { Icon(Icons.Default.Send, "send") }
        }
    }

    if (showPrompts) {
        AlertDialog(
            onDismissRequest = { showPrompts = false },
            title = { Text("Saved prompts") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (prompts.isEmpty()) Text("कोई prompt सेव नहीं है।")
                    prompts.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.text, Modifier.weight(1f).clickable { input = p.text; showPrompts = false }.padding(vertical = 8.dp), maxLines = 2)
                            IconButton(onClick = { vm.deletePrompt(p) }) { Icon(Icons.Default.Delete, "delete") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.savePrompt(input); showPrompts = false }) { Text("मौजूदा टेक्स्ट सेव करें") } },
            dismissButton = { TextButton(onClick = { showPrompts = false }) { Text("Close") } }
        )
    }
}
