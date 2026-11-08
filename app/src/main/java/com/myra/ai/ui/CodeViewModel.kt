package com.myra.ai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myra.ai.MyraApplication
import com.myra.ai.coding.ProjectStore
import com.myra.ai.coding.Shell
import com.myra.ai.coding.Templates
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class CodeViewModel(app: Application) : AndroidViewModel(app) {
    private val my = app as MyraApplication
    val store = ProjectStore(app)

    val projects = MutableStateFlow(store.projects())
    val project = MutableStateFlow<String?>(null)
    val files = MutableStateFlow<List<String>>(emptyList())
    val openFile = MutableStateFlow<String?>(null)
    val code = MutableStateFlow("")
    val lang = MutableStateFlow("Kotlin")
    val busy = MutableStateFlow(false)
    val aiText = MutableStateFlow<String?>(null)
    val msg = MutableStateFlow<String?>(null)
    val term = MutableStateFlow<List<String>>(emptyList())
    val buildLog = MutableStateFlow<List<String>>(emptyList())
    val building = MutableStateFlow(false)
    val apk = MutableStateFlow<File?>(null)
    private var job: Job? = null

    private fun refresh() {
        projects.value = store.projects()
        files.value = project.value?.let { store.files(it) } ?: emptyList()
    }

    fun select(p: String) { project.value = p; openFile.value = null; code.value = ""; apk.value = store.apk(p); refresh() }

    fun create(name: String, android: Boolean) {
        try {
            store.create(name, if (android) Templates.androidProject(name) else emptyMap())
            select(name)
            msg.value = "प्रोजेक्ट बन गया।"
        } catch (e: Exception) { msg.value = e.message }
    }

    fun open(rel: String) {
        val p = project.value ?: return
        try { code.value = store.read(p, rel); openFile.value = rel } catch (e: Exception) { msg.value = "फ़ाइल नहीं खुली: ${e.message}" }
    }

    fun save() {
        val p = project.value ?: run { msg.value = "पहले प्रोजेक्ट चुनें।"; return }
        val f = openFile.value ?: run { msg.value = "पहले फ़ाइल चुनें या नई बनाएँ।"; return }
        try { store.write(p, f, code.value); refresh(); msg.value = "सेव हो गया।" } catch (e: Exception) { msg.value = e.message }
    }

    fun newFile(rel: String) {
        val p = project.value ?: return
        try { store.write(p, rel.trim(), ""); refresh(); open(rel.trim()) } catch (e: Exception) { msg.value = e.message }
    }

    fun deleteFile(rel: String) {
        val p = project.value ?: return
        store.delete(p, rel); if (openFile.value == rel) { openFile.value = null; code.value = "" }; refresh()
    }

    fun deleteProject() {
        val p = project.value ?: return
        store.deleteProject(p); project.value = null; openFile.value = null; code.value = ""; refresh()
    }

    fun importZip(uri: Uri, name: String) {
        try { store.importZip(uri, name); select(name); msg.value = "इम्पोर्ट हो गया।" } catch (e: Exception) { msg.value = "Import विफल: ${e.message}" }
    }

    fun exportZip() {
        val p = project.value ?: return
        viewModelScope.launch {
            try { ProjectStore.share(my, store.zip(p), "application/zip") } catch (e: Exception) { msg.value = e.message }
        }
    }

    fun exportApk() { apk.value?.let { ProjectStore.share(my, it, "application/vnd.android.package-archive") } }

    private fun extractCode(t: String): String {
        val m = Regex("```[a-zA-Z+#]*\\n([\\s\\S]*?)```").find(t)
        return m?.groupValues?.get(1)?.trimEnd() ?: t
    }

    /** kind: generate | explain | fix | optimize */
    fun ai(kind: String, extra: String = "") {
        if (busy.value) return
        val l = lang.value
        val sys = "You are an expert programmer. Be precise and correct."
        val prompt = when (kind) {
            "generate" -> "Write $l code for: $extra\nReturn the complete code in ONE fenced code block, then 2 lines of notes."
            "explain" -> "Explain this $l code clearly, step by step, in simple Hindi-English:\n```\n${code.value}\n```"
            "fix" -> "Find and fix bugs in this $l code. $extra\nReturn the FULL corrected code in ONE fenced block, then a short list of what changed:\n```\n${code.value}\n```"
            else -> "Optimize this $l code for readability and performance. Return the FULL code in ONE fenced block, then brief notes:\n```\n${code.value}\n```"
        }
        busy.value = true
        viewModelScope.launch {
            val r = my.assistant.oneShot(prompt, sys)
            busy.value = false
            if (r.isError) { msg.value = r.text; return@launch }
            if (kind == "explain") aiText.value = r.text
            else {
                code.value = extractCode(r.text)
                aiText.value = r.text.substringAfterLast("```", "").trim().ifBlank { null }
                if (openFile.value == null && project.value != null) {
                    val name = "generated_${System.currentTimeMillis() % 100000}.${Templates.ext(l)}"
                    openFile.value = name
                }
            }
        }
    }

    fun runTerminal(cmd: String) {
        val dir = project.value?.let { store.dir(it) } ?: my.filesDir
        term.update { it + "$ $cmd" }
        job?.cancel()
        job = viewModelScope.launch {
            try {
                Shell.run(cmd, dir).collect { l -> term.update { (it + l).takeLast(500) } }
            } catch (e: Exception) { term.update { it + "error: ${e.message}" } }
        }
    }

    fun stopJob() { job?.cancel(); building.value = false }

    fun build() {
        val p = project.value ?: run { msg.value = "पहले प्रोजेक्ट चुनें।"; return }
        buildLog.value = listOf("Building $p …")
        building.value = true
        apk.value = null
        job?.cancel()
        job = viewModelScope.launch {
            val cmd = "if [ -x ./gradlew ]; then ./gradlew assembleDebug --console=plain; else gradle assembleDebug --console=plain; fi"
            try {
                Shell.run(cmd, store.dir(p)).collect { l -> buildLog.update { (it + l).takeLast(2000) } }
            } catch (e: Exception) { buildLog.update { it + "error: ${e.message}" } }
            apk.value = store.apk(p)
            building.value = false
            buildLog.update { it + if (apk.value != null) "✅ APK तैयार: ${apk.value!!.name}" else "❌ APK नहीं बना (ऊपर का log देखें)" }
        }
    }

    fun explainBuild() {
        val tail = buildLog.value.takeLast(80).joinToString("\n")
        busy.value = true
        viewModelScope.launch {
            val r = my.assistant.oneShot(
                "Explain why this Gradle/Android build failed and how to fix it, step by step:\n$tail",
                "You are an Android build expert."
            )
            busy.value = false
            aiText.value = r.text
        }
    }
}
