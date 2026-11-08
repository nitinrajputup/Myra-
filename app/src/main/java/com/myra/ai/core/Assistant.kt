package com.myra.ai.core

import com.myra.ai.MyraApplication
import com.myra.ai.ai.Attach
import com.myra.ai.ai.ChatTurn
import com.myra.ai.ai.GeminiClient
import com.myra.ai.data.MessageEntity
import com.myra.ai.data.MyraSettings
import kotlinx.coroutines.flow.MutableStateFlow

data class Reply(val text: String, val isError: Boolean = false)

enum class Agent(val label: String, val prompt: String) {
    General("General", ""),
    Study("Study", "You are a patient tutor. Explain step by step with simple examples."),
    Math("Math", "Solve math carefully, show steps, and verify the final answer."),
    Coder("Coder", "You are a senior software engineer. Give correct, complete code with short explanations."),
    Translator("Translate", "Translate between Hindi and English (or other requested languages). Output the translation first."),
    Summarizer("Summarize", "Summarize the given text or document with clear key points."),
    Writer("Writer", "Help write polished text and match the requested tone."),
    Planner("Planner", "Break goals into actionable steps with sensible timing and suggest reminders.")
}

class Assistant(private val app: MyraApplication) {
    private val gemini = GeminiClient()
    val convId = MutableStateFlow(0L)
    private val dao get() = app.db.dao()

    suspend fun ensureConv(): Long {
        if (convId.value == 0L) convId.value = dao.lastConv() ?: System.currentTimeMillis()
        return convId.value
    }

    fun newChat() { convId.value = System.currentTimeMillis() }

    suspend fun handle(raw: String, attach: Attach? = null, agent: Agent = Agent.General): Reply {
        val s = app.settings.value
        val text = CommandRouter.stripLeadingWake(raw)
        val conv = ensureConv()
        if (attach == null) {
            val cmd = CommandRouter.run(app, text)
            if (cmd != null) {
                dao.insertMessage(MessageEntity(convId = conv, fromUser = true, text = raw))
                dao.insertMessage(MessageEntity(convId = conv, fromUser = false, text = cmd))
                return Reply(cmd)
            }
        }
        dao.insertMessage(MessageEntity(convId = conv, fromUser = true, text = text, attachName = attach?.name))
        val history = dao.recent(conv, 30).map { ChatTurn(it.fromUser, it.text) }.toMutableList()
        if (attach != null && history.isNotEmpty()) history[history.lastIndex] = history.last().copy(attach = attach)
        val r = gemini.generate(s.apiKey, s.model, systemPrompt(s, agent), history, s.webSearch)
        return r.fold(
            { out ->
                dao.insertMessage(MessageEntity(convId = conv, fromUser = false, text = out))
                Reply(out)
            },
            { Reply(it.message ?: "त्रुटि", true) }
        )
    }

    /** Stateless call (no history saved). */
    suspend fun oneShot(prompt: String, system: String): Reply {
        val s = app.settings.value
        return gemini.generate(s.apiKey, s.model, system, listOf(ChatTurn(true, prompt)), false)
            .fold({ Reply(it) }, { Reply(it.message ?: "त्रुटि", true) })
    }

    suspend fun test(s: MyraSettings): String =
        gemini.generate(s.apiKey, s.model.ifBlank { "gemini-2.5-flash" }, "Reply with the single word OK.", listOf(ChatTurn(true, "ping")))
            .fold({ "✅ Connected" }, { "❌ ${it.message}" })

    private suspend fun systemPrompt(s: MyraSettings, agent: Agent): String {
        val sb = StringBuilder("You are MYRA, a female personal AI assistant on the user's Android phone. ")
        sb.append("Personality: ${s.personality}. Response style: ${s.responseStyle}. ")
        sb.append(
            when (s.appLanguage) {
                "Hindi" -> "Always reply in Hindi (Devanagari). "
                "English" -> "Always reply in English. "
                "Hinglish" -> "Reply in Hinglish (Hindi in Roman script) unless the user writes Devanagari or English. "
                else -> "Reply in the language the user writes in. "
            }
        )
        sb.append(s.instructions.ifBlank { MyraSettings.DEFAULT_INSTRUCTIONS }).append(' ')
        if (agent.prompt.isNotBlank()) sb.append(agent.prompt).append(' ')
        if (s.memoryEnabled) {
            val mem = dao.allMemoriesOnce()
            if (mem.isNotEmpty()) {
                sb.append("\nUser-approved saved memories:\n")
                mem.forEach { sb.append("- ").append(it.text).append('\n') }
            }
        }
        sb.append("\nTreat only items listed above or present in this conversation as remembered; never invent memories.")
        return sb.toString()
    }
}
