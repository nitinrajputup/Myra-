package com.myra.ai.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.ai.MyraApplication
import com.myra.ai.automation.Automation
import com.myra.ai.core.Actions
import com.myra.ai.data.MemoryEntity
import com.myra.ai.ui.MainViewModel
import com.myra.ai.ui.components.Glass
import com.myra.ai.ui.components.SwitchRow
import com.myra.ai.ui.components.Title
import kotlinx.coroutines.launch

@Composable
fun AutomationScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as MyraApplication
    val scope = rememberCoroutineScope()
    val logs by Automation.logs.collectAsState()
    val status by Automation.status.collectAsState()
    val plan by vm.plan.collectAsState()
    val planMsg by vm.planMsg.collectAsState()
    val videos by vm.videos.collectAsState()
    val ytMsg by vm.ytMsg.collectAsState()
    var clickT by remember { mutableStateOf("") }
    var typeT by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }
    var yq by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    val enabled = remember(tick, status) { a11yEnabled(ctx) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Glass {
            Title("Screen Control (Accessibility)")
            Text(
                "MYRA सिर्फ़ आपके आदेश पर टैप/स्क्रॉल/क्लिक करती है। यह अनुमति आपको Android Settings में खुद चालू करनी होगी। " +
                    "संवेदनशील क्रियाओं (pay, send, delete…) पर पहले पुष्टि माँगी जाती है, और पासवर्ड फ़ील्ड कभी नहीं पढ़ी जातीं।",
                color = Color.Gray, fontSize = 12.sp
            )
            Text(if (enabled) "✅ सेवा चालू है" else "⚪ सेवा बंद है", color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Accessibility Settings") }
                OutlinedButton(onClick = { tick++ }) { Text("Refresh") }
            }
            Text("Status: $status", color = Color.White, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Automation.emergencyStop(app) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("⛔ EMERGENCY STOP") }
                OutlinedButton(onClick = { Automation.resume() }) { Text("Resume") }
            }
        }
        Glass {
            Title("Quick actions")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { scope.launch { Actions.exec(app, "scroll_down") } }) { Text("↓") }
                OutlinedButton(onClick = { scope.launch { Actions.exec(app, "scroll_up") } }) { Text("↑") }
                OutlinedButton(onClick = { scope.launch { Actions.exec(app, "back") } }) { Text("Back") }
                OutlinedButton(onClick = { scope.launch { Actions.exec(app, "home") } }) { Text("Home") }
            }
            OutlinedButton(onClick = { scope.launch { Automation.log(Actions.exec(app, "read_screen")) } }) { Text("Read screen") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(clickT, { clickT = it }, Modifier.weight(1f), singleLine = true, label = { Text("Click text") })
                Button(onClick = { scope.launch { Actions.exec(app, "click_text", clickT) } }) { Text("Click") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(typeT, { typeT = it }, Modifier.weight(1f), singleLine = true, label = { Text("Type into focused field") })
                Button(onClick = { scope.launch { Actions.exec(app, "type_text", typeT) } }) { Text("Type") }
            }
        }
        Glass {
            Title("AI Agent (multi-step)")
            OutlinedTextField(goal, { goal = it }, Modifier.fillMaxWidth(), label = { Text("लक्ष्य, जैसे: YouTube खोलकर lofi music खोजो") }, minLines = 2)
            Button(onClick = { vm.makePlan(goal) }, enabled = goal.isNotBlank()) { Text("Plan बनाओ") }
            planMsg?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
            plan.forEachIndexed { i, s -> Text("${i + 1}. ${s.action} ${s.arg}", color = Color.White, fontSize = 13.sp) }
            if (plan.isNotEmpty()) Button(onClick = { vm.runPlan() }) { Text("✔ Approve & Run") }
        }
        Glass {
            Title("YouTube Music")
            Text("MYRA के अंदर नतीजे दिखाने के लिए Settings में YouTube Data API key चाहिए। बिना key के voice command सीधे YouTube खोलता है।", color = Color.Gray, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(yq, { yq = it }, Modifier.weight(1f), singleLine = true, label = { Text("गाना / artist") })
                Button(onClick = { vm.ytSearch(yq) }, enabled = yq.isNotBlank()) { Text("Search") }
            }
            OutlinedButton(onClick = { scope.launch { Actions.exec(app, "youtube_search", yq) } }, enabled = yq.isNotBlank()) { Text("YouTube ऐप में खोलो") }
            ytMsg?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp) }
            videos.forEach { v ->
                Column(Modifier.fillMaxWidth().clickable { vm.openVideo(v.id) }.padding(vertical = 6.dp)) {
                    Text(v.title, color = Color.White, fontSize = 14.sp, maxLines = 2)
                    Text(v.channel, color = Color.Gray, fontSize = 11.sp)
                }
            }
            Text("Background playback / Premium features का दावा नहीं — वीडियो YouTube ऐप खोलता है।", color = Color.Gray, fontSize = 10.sp)
        }
        Glass {
            Title("Automation log")
            logs.takeLast(40).forEach { Text(it, color = Color(0xFFCCCCCC), fontSize = 11.sp) }
            if (logs.isEmpty()) Text("अभी कोई गतिविधि नहीं।", color = Color.Gray)
        }
    }
}

@Composable
fun MemoryScreen(vm: MainViewModel) {
    val mems by vm.memories.collectAsState()
    val s by vm.settings.collectAsState()
    val mh by vm.memHits.collectAsState()
    val gh by vm.msgHits.collectAsState()
    var add by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var editText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Glass {
            Title("Memory")
            SwitchRow("Saved memories इस्तेमाल करें", s.memoryEnabled, { vm.save(s.copy(memoryEnabled = it)) },
                "बंद करने पर Gemini को आपकी सेव की हुई बातें नहीं भेजी जाएँगी")
            Text("सिर्फ़ वही याद रखा जाता है जो आप कहते हैं (\"मेरा नाम X याद रखना\") या यहाँ जोड़ते हैं। सब कुछ सिर्फ़ इसी फ़ोन में रहता है।", color = Color.Gray, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(add, { add = it }, Modifier.weight(1f), singleLine = true, label = { Text("नई memory") })
                Button(onClick = { vm.addMemory(add); add = "" }, enabled = add.isNotBlank()) { Text("Add") }
            }
        }
        Glass {
            Title("खोजें (memory + चैट history)")
            OutlinedTextField(q, { q = it; vm.search(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") })
            mh.forEach { Text("🧠 ${it.text}", color = Color.White, fontSize = 13.sp) }
            gh.forEach { Text((if (it.fromUser) "आप: " else "MYRA: ") + it.text.take(160), color = Color(0xFFCCCCCC), fontSize = 12.sp) }
        }
        Glass {
            Title("सेव की हुई memories (${mems.size})")
            if (mems.isEmpty()) Text("अभी कुछ नहीं।", color = Color.Gray)
            mems.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.text, Modifier.weight(1f), color = Color.White)
                    IconButton(onClick = { editing = m; editText = m.text }) { Icon(Icons.Default.Edit, "edit") }
                    IconButton(onClick = { vm.deleteMemory(m) }) { Icon(Icons.Default.Delete, "delete") }
                }
            }
            if (mems.isNotEmpty()) OutlinedButton(onClick = { vm.clearMemories() }) { Text("सारी memory मिटाएँ") }
        }
    }
    editing?.let { m ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit memory") },
            text = { OutlinedTextField(editText, { editText = it }) },
            confirmButton = { TextButton(onClick = { vm.editMemory(m, editText); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}
