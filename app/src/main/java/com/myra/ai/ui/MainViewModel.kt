package com.myra.ai.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.myra.ai.MyraApplication
import com.myra.ai.ai.Attach
import com.myra.ai.coding.ProjectStore
import com.myra.ai.core.Agent
import com.myra.ai.core.AgentPlanner
import com.myra.ai.core.Step
import com.myra.ai.core.YouTubeApi
import com.myra.ai.data.*
import com.myra.ai.device.AppLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val my = app as MyraApplication
    private val dao = my.db.dao()
    val settings = my.settings

    val messages: StateFlow<List<MessageEntity>> =
        my.assistant.convId.flatMapLatest { dao.conv(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memories = dao.memories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val prompts = dao.prompts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val agent = MutableStateFlow(Agent.General)
    val attach = MutableStateFlow<Attach?>(null)
    val testResult = MutableStateFlow<String?>(null)

    init { viewModelScope.launch { my.assistant.ensureConv() } }

    fun send(text: String) {
        if (loading.value || (text.isBlank() && attach.value == null)) return
        viewModelScope.launch {
            loading.value = true
            error.value = null
            val a = attach.value
            attach.value = null
            val r = my.assistant.handle(text, a, agent.value)
            if (r.isError) error.value = r.text
            loading.value = false
        }
    }

    fun newChat() { my.assistant.newChat() }

    fun attachUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val cr = getApplication<Application>().contentResolver
                val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                if (bytes.size > 15_000_000) { error.value = "फ़ाइल 15MB से बड़ी है।"; return@launch }
                val mime = cr.getType(uri) ?: "application/octet-stream"
                var name = "file"
                cr.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i) }
                }
                attach.value = Attach(mime, Base64.encodeToString(bytes, Base64.NO_WRAP), name)
            } catch (e: Exception) {
                error.value = "फ़ाइल नहीं पढ़ी जा सकी: ${e.message}"
            }
        }
    }

    // settings
    fun save(s: MyraSettings) { viewModelScope.launch { my.store.save(s) } }
    fun test(s: MyraSettings) {
        testResult.value = "Testing…"
        viewModelScope.launch { testResult.value = my.assistant.test(s) }
    }

    // memory
    fun addMemory(t: String) { if (t.isNotBlank()) viewModelScope.launch { dao.insertMemory(MemoryEntity(text = t.trim())) } }
    fun editMemory(m: MemoryEntity, t: String) { viewModelScope.launch { dao.updateMemory(m.copy(text = t.trim())) } }
    fun deleteMemory(m: MemoryEntity) { viewModelScope.launch { dao.deleteMemory(m) } }
    fun clearMemories() { viewModelScope.launch { dao.clearMemories() } }
    fun clearHistory() { viewModelScope.launch { dao.clearMessages(); my.assistant.newChat() } }

    val memHits = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val msgHits = MutableStateFlow<List<MessageEntity>>(emptyList())
    fun search(q: String) {
        viewModelScope.launch {
            if (q.isBlank()) { memHits.value = emptyList(); msgHits.value = emptyList() }
            else { memHits.value = dao.searchMemories(q); msgHits.value = dao.searchMessages(q) }
        }
    }

    // prompts
    fun savePrompt(text: String) { if (text.isNotBlank()) viewModelScope.launch { dao.insertPrompt(PromptEntity(title = text.take(30), text = text)) } }
    fun deletePrompt(p: PromptEntity) { viewModelScope.launch { dao.deletePrompt(p) } }

    fun exportData() {
        viewModelScope.launch {
            val o = JSONObject()
            o.put("memories", JSONArray().also { a -> dao.allMemoriesOnce().forEach { a.put(JSONObject().put("text", it.text).put("ts", it.ts)) } })
            o.put("messages", JSONArray().also { a ->
                dao.allMessages().forEach { a.put(JSONObject().put("conv", it.convId).put("user", it.fromUser).put("text", it.text).put("ts", it.ts)) }
            })
            val f = File(File(my.cacheDir, "exports").apply { mkdirs() }, "myra-export.json")
            f.writeText(o.toString(2))
            ProjectStore.share(my, f, "application/json")
        }
    }

    // automation / agent
    val plan = MutableStateFlow<List<Step>>(emptyList())
    val planMsg = MutableStateFlow<String?>(null)
    fun makePlan(goal: String) {
        planMsg.value = "Plan बना रही हूँ…"
        viewModelScope.launch {
            AgentPlanner.plan(my, goal)
                .onSuccess { plan.value = it; planMsg.value = "Plan तैयार — Run दबाने से पहले जाँच लें।" }
                .onFailure { plan.value = emptyList(); planMsg.value = it.message }
        }
    }
    fun runPlan() { val p = plan.value; viewModelScope.launch { AgentPlanner.run(my, p) } }

    val videos = MutableStateFlow<List<YouTubeApi.Video>>(emptyList())
    val ytMsg = MutableStateFlow<String?>(null)
    fun ytSearch(q: String) {
        viewModelScope.launch {
            YouTubeApi.search(settings.value.ytKey, q)
                .onSuccess { videos.value = it; ytMsg.value = if (it.isEmpty()) "कोई परिणाम नहीं।" else null }
                .onFailure { ytMsg.value = it.message }
        }
    }
    fun openVideo(id: String) { AppLauncher.youtubeVideo(my, id) }
}
