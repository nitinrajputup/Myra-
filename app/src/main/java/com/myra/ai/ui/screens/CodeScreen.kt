package com.myra.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myra.ai.coding.Templates
import com.myra.ai.ui.CodeViewModel
import com.myra.ai.ui.components.Chip
import com.myra.ai.ui.components.Glass
import com.myra.ai.ui.components.Pick

private val kwRe = Regex(
    "\\b(fun|val|var|class|object|interface|if|else|for|while|when|return|import|package|def|from|as|in|is|try|catch|finally|throw|" +
        "new|public|private|protected|static|final|void|int|long|float|double|char|bool|boolean|String|true|false|null|None|True|False|" +
        "function|const|let|async|await|select|insert|update|delete|where|create|table|struct|include|using|namespace|template|" +
        "override|suspend|data|enum|this|self|lambda|print|SELECT|FROM|WHERE|INSERT|INTO|VALUES|CREATE|TABLE)\\b"
)
private val numRe = Regex("\\b\\d+(\\.\\d+)?\\b")
private val strRe = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'")
private val cmtRe = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\n]*")

fun highlight(src: String): AnnotatedString = buildAnnotatedString {
    append(src)
    if (src.length > 30000) return@buildAnnotatedString
    fun paint(re: Regex, c: Long) = re.findAll(src).forEach { addStyle(SpanStyle(color = Color(c)), it.range.first, it.range.last + 1) }
    paint(kwRe, 0xFFC792EA); paint(numRe, 0xFFF78C6C); paint(strRe, 0xFFC3E88D); paint(cmtRe, 0xFF7F8C98)
}

private class Syntax : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlight(text.text), OffsetMapping.Identity)
}

@Composable
fun CodeScreen(vm: CodeViewModel) {
    val projects by vm.projects.collectAsState()
    val project by vm.project.collectAsState()
    val files by vm.files.collectAsState()
    val openFile by vm.openFile.collectAsState()
    val code by vm.code.collectAsState()
    val lang by vm.lang.collectAsState()
    val busy by vm.busy.collectAsState()
    val aiText by vm.aiText.collectAsState()
    val msg by vm.msg.collectAsState()
    val term by vm.term.collectAsState()
    val buildLog by vm.buildLog.collectAsState()
    val building by vm.building.collectAsState()
    val apk by vm.apk.collectAsState()
    val clip = LocalClipboardManager.current

    var tab by remember { mutableStateOf("Editor") }
    var dialog by remember { mutableStateOf<String?>(null) }
    var field by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importZip(it, "imported_" + System.currentTimeMillis() % 10000) }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pick("Project", project ?: "—", projects) { vm.select(it) }
            OutlinedButton(onClick = { field = ""; dialog = "new" }) { Text("New") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { field = ""; dialog = "android" }) { Text("Android proj") }
            OutlinedButton(onClick = { picker.launch(arrayOf("application/zip")) }) { Text("Import") }
            OutlinedButton(onClick = { vm.exportZip() }, enabled = project != null) { Text("Export") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Editor", "Files", "Terminal", "Build").forEach { Chip(it, tab == it) { tab = it } }
        }
        msg?.let { Text(it, color = Color(0xFFFFC107), fontSize = 12.sp) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        when (tab) {
            "Editor" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pick("Lang", lang, Templates.languages) { vm.lang.value = it }
                    Text(openFile ?: "(no file)", color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                }
                OutlinedTextField(
                    code, { vm.code.value = it }, Modifier.fillMaxWidth().heightIn(min = 280.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                    visualTransformation = Syntax()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { vm.save() }) { Text("Save") }
                    OutlinedButton(onClick = { clip.setText(AnnotatedString(code)) }) { Text("Copy") }
                    OutlinedButton(onClick = { field = ""; dialog = "gen" }, enabled = !busy) { Text("AI Generate") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.ai("explain") }, enabled = !busy && code.isNotBlank()) { Text("Explain") }
                    OutlinedButton(onClick = { field = ""; dialog = "fix" }, enabled = !busy && code.isNotBlank()) { Text("Fix") }
                    OutlinedButton(onClick = { vm.ai("optimize") }, enabled = !busy && code.isNotBlank()) { Text("Optimize") }
                }
            }
            "Files" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { field = ""; dialog = "file" }, enabled = project != null) { Text("New file") }
                    OutlinedButton(onClick = { vm.deleteProject() }, enabled = project != null) { Text("Delete project") }
                }
                files.forEach { f ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(f, Modifier.weight(1f).clickable { vm.open(f); tab = "Editor" }.padding(vertical = 10.dp), color = Color.White, fontSize = 13.sp)
                        IconButton(onClick = { vm.deleteFile(f) }) { Icon(Icons.Default.Delete, "delete") }
                    }
                }
            }
            "Terminal" -> Column(Modifier.weight(1f)) {
                LazyColumn(Modifier.weight(1f).background(Color(0xFF05050A), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    items(term) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF9EE493)) }
                }
                Text("App sandbox में चलता है (सीमित). Gradle build के लिए Termux उपयोग करें।", color = Color.Gray, fontSize = 10.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(cmd, { cmd = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("command") })
                    Button(onClick = { vm.runTerminal(cmd); cmd = "" }) { Text("Run") }
                }
            }
            else -> Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { vm.build() }, enabled = !building && project != null) { Text("Build APK") }
                    if (building) OutlinedButton(onClick = { vm.stopJob() }) { Text("Stop") }
                    OutlinedButton(onClick = { vm.explainBuild() }, enabled = !busy && buildLog.isNotEmpty() && !building) { Text("Explain") }
                    OutlinedButton(onClick = { vm.exportApk() }, enabled = apk != null) { Text("Export APK") }
                }
                if (building) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                LazyColumn(Modifier.weight(1f).background(Color(0xFF05050A), RoundedCornerShape(12.dp)).padding(8.dp)) {
                    items(buildLog) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFCCCCCC)) }
                }
            }
        }
    }

    dialog?.let { d ->
        val title = when (d) { "new" -> "New project name"; "android" -> "Android project name"; "gen" -> "क्या बनाना है?"; "fix" -> "कोई hint (optional)"; else -> "File path (e.g. src/main.py)" }
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(title) },
            text = { OutlinedTextField(field, { field = it }, minLines = if (d == "gen") 3 else 1) },
            confirmButton = {
                TextButton(onClick = {
                    when (d) {
                        "new" -> vm.create(field.trim(), false)
                        "android" -> vm.create(field.trim(), true)
                        "gen" -> vm.ai("generate", field)
                        "fix" -> vm.ai("fix", field)
                        else -> vm.newFile(field)
                    }
                    dialog = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } }
        )
    }
    aiText?.let {
        AlertDialog(
            onDismissRequest = { vm.aiText.value = null },
            title = { Text("MYRA") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(it) } },
            confirmButton = { TextButton(onClick = { vm.aiText.value = null }) { Text("Close") } }
        )
    }
}
