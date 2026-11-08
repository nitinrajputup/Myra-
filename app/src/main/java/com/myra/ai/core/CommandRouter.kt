package com.myra.ai.core

import com.myra.ai.MyraApplication
import com.myra.ai.automation.Automation
import com.myra.ai.data.MemoryEntity
import com.myra.ai.device.AppLauncher
import com.myra.ai.device.BatteryInfo
import com.myra.ai.device.Reminders
import java.util.Calendar

data class WakeHit(val greeted: Boolean, val rest: String)

object CommandRouter {
    private val wake = Regex(
        "(hello|hey|hi|हेलो|हैलो|हे)?\\s*(myra|maira|mira|meera|मायरा|मैरा|मीरा)",
        RegexOption.IGNORE_CASE
    )

    fun findWake(t: String): WakeHit? {
        val m = wake.find(t) ?: return null
        val rest = t.substring(m.range.last + 1).trim(' ', ',', '.', '।')
        return WakeHit(m.groupValues[1].isNotBlank(), rest)
    }

    fun stripLeadingWake(t: String): String {
        val m = wake.find(t)
        return if (m != null && m.range.first <= 1) t.substring(m.range.last + 1).trim(' ', ',', '.', '।') else t.trim()
    }

    private fun norm(w: String) = w.lowercase().trim(',', '.', '।', '?', '!')
    private fun clean(words: List<String>, drop: Set<String>) =
        words.filter { norm(it) !in drop }.joinToString(" ").trim()

    private val musicWords = setOf("gana", "gaana", "गाना", "गाने", "song", "songs", "music", "गीत", "संगीत")
    private val playWords = setOf("chalao", "chala", "चलाओ", "चलाइए", "play", "bajao", "बजाओ", "sunao", "सुनाओ", "lagao", "लगाओ")
    private val hindiOpen = setOf("kholo", "khol", "खोलो", "खोल", "kholiye", "खोलिए")
    private val fillers = setOf(
        "karo", "करो", "do", "दो", "ko", "को", "app", "ऐप", "please", "plz", "myra", "जी", "na",
        "par", "पर", "pe", "me", "mein", "में", "ka", "ki", "ke", "का", "की", "के", "ek", "एक",
        "koi", "कोई", "mujhe", "मुझे", "for", "youtube", "यूट्यूब", "karke", "करके", "the", "this", "on", "is", "इस", "button", "बटन"
    )

    /** Returns the spoken/displayed reply if the text was a phone command, otherwise null (→ Gemini). */
    suspend fun run(app: MyraApplication, raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val t = text.lowercase()
        val rw = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val nw = rw.map { norm(it) }
        fun has(vararg s: String) = nw.any { it in s }

        // --- emergency stop
        if (rw.size <= 3 && has("stop", "ruko", "रुको", "ruk", "band")) {
            Automation.emergencyStop(app)
            return "ठीक है, रुक गई।"
        }
        Automation.stopped = false

        val hasYt = t.contains("youtube") || t.contains("यूट्यूब")
        val searchW = listOf("search", "सर्च", "खोजो", "khojo", "dhundho")

        // --- YouTube search
        if (hasYt && searchW.any { t.contains(it) } && rw.size <= 12) {
            val q = clean(rw, fillers + hindiOpen + setOf("open", "search", "सर्च", "khojo", "खोजो", "dhundho", "kare", "kar"))
            return if (q.isBlank()) Actions.exec(app, "open_app", "YouTube") else Actions.exec(app, "youtube_search", q)
        }

        // --- music
        if (nw.any { it in musicWords } && nw.any { it in playWords }) {
            var q = clean(rw, fillers + playWords + setOf("gana", "gaana", "गाना", "गाने", "गीत"))
            if (q.isBlank()) q = "trending songs"
            else if (!q.lowercase().contains("song") && !q.lowercase().contains("music") &&
                !q.contains("गाना") && !q.contains("संगीत")
            ) q += " songs"
            return Actions.exec(app, "youtube_search", q)
        }

        // --- open app
        if (rw.size <= 6 && (nw.any { it in hindiOpen } || (nw.first() == "open" && rw.size <= 4))) {
            val name = clean(rw, fillers + hindiOpen + setOf("open"))
            if (name.isNotBlank()) return Actions.exec(app, "open_app", name)
        }

        // --- scroll
        if (t.contains("scroll") || t.contains("स्क्रॉल")) {
            val up = listOf("upar", "ऊपर", " up").any { t.contains(it) } &&
                listOf("neeche", "niche", "नीचे", "down").none { t.contains(it) }
            return Actions.exec(app, if (up) "scroll_up" else "scroll_down")
        }

        // --- click
        if (has("click", "tap", "क्लिक", "दबाओ", "दबाइए", "press")) {
            val target = clean(rw, fillers + setOf("click", "tap", "क्लिक", "दबाओ", "दबाइए", "press"))
            if (target.isBlank()) return "किस बटन पर क्लिक करूँ? बटन का नाम बोलिए।"
            return Actions.exec(app, "click_text", target)
        }

        if (rw.size <= 3 && has("back", "peeche", "पीछे")) return Actions.exec(app, "back")
        if (rw.size <= 3 && has("home", "होम")) return Actions.exec(app, "home")

        // --- memory: save
        if (listOf("yaad rakh", "याद रख", "remember").any { t.contains(it) } && !t.contains("remember to")) {
            val content = clean(
                rw, fillers + setOf(
                    "yaad", "याद", "rakhna", "rakho", "rakhiye", "रखना", "रखो", "रखिए", "remember",
                    "that", "ye", "ये", "karlo", "kar", "lo"
                )
            )
            if (!app.settings.value.memoryEnabled) return "Memory बंद है। पहले Settings में चालू करें।"
            if (content.lowercase() in setOf("mera naam", "my name", "मेरा नाम", "") ) {
                return "आपका नाम क्या है? बोलिए: मेरा नाम ... याद रखना।"
            }
            app.db.dao().insertMemory(MemoryEntity(text = content))
            return "ठीक है, याद रख लिया: $content"
        }

        // --- memory: list
        if (listOf("kya yaad hai", "क्या याद है", "what do you remember", "mere bare me kya").any { t.contains(it) }) {
            val m = app.db.dao().allMemoriesOnce()
            return if (m.isEmpty()) "मेरे पास अभी कुछ सेव नहीं है।"
            else "मुझे ये याद है: " + m.takeLast(10).joinToString("; ") { it.text }
        }

        // --- recall yesterday
        if (listOf("kal kya kaha", "कल क्या कहा", "maine kal kya").any { t.contains(it) }) {
            val c = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val today = c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, -1)
            val msgs = app.db.dao().between(c.timeInMillis, today - 1).filter { it.fromUser }
            if (msgs.isEmpty()) return "कल की कोई सेव की हुई बातचीत नहीं मिली।"
            val r = app.assistant.oneShot(
                "These are the user's saved messages from yesterday:\n" + msgs.joinToString("\n") { "- " + it.text } +
                    "\n\nTell the user briefly what they said yesterday, in Hindi.",
                "You summarize saved chat history faithfully. Do not add anything that is not in the list."
            )
            return r.text
        }

        // --- reminder
        if (listOf("reminder", "रिमाइंडर", "yaad dila", "याद दिला", "remind").any { t.contains(it) }) {
            val m = Regex("(\\d+)\\s*(minute|min|मिनट|ghante|ghanta|hour|hr|घंटे|घंटा)").find(t)
                ?: return "कितने मिनट या घंटे बाद याद दिलाऊँ? जैसे: 10 मिनट बाद रिमाइंडर।"
            val n = m.groupValues[1].toLong()
            val u = m.groupValues[2]
            val mins = if (u.startsWith("min") || u == "मिनट") n else n * 60
            val msg = clean(
                t.replace(m.value, " ").split(Regex("\\s+")).filter { it.isNotBlank() },
                fillers + setOf("baad", "बाद", "reminder", "रिमाइंडर", "yaad", "dila", "dilana", "दिलाना", "दिला", "set", "remind", "to", "ye", "ये")
            ).ifBlank { "Reminder" }
            Reminders.schedule(app, mins, msg)
            val whenTxt = if (mins >= 60 && mins % 60 == 0L) "${mins / 60} घंटे" else "$mins मिनट"
            return "ठीक है, $whenTxt बाद याद दिलाऊँगी: $msg"
        }

        // --- calendar
        if (t.contains("calendar") || t.contains("कैलेंडर")) {
            val title = clean(rw, fillers + setOf("calendar", "कैलेंडर", "add", "event", "जोड़ो", "में"))
            Reminders.calendar(app, title.ifBlank { "MYRA event" })
            return "कैलेंडर में इवेंट खोल रही हूँ, आप सेव कर दीजिए।"
        }

        // --- battery
        if ((t.contains("battery") || t.contains("बैटरी")) &&
            listOf("kitni", "कितनी", "status", "level", "percent", "%", "how much").any { t.contains(it) }
        ) {
            val b = BatteryInfo.read(app)
            return "बैटरी ${b.percent}% है" + if (b.charging) " और चार्ज हो रही है।" else "।"
        }
        return null
    }
}
