package com.myra.ai.core

import com.myra.ai.MyraApplication
import com.myra.ai.automation.Automation
import kotlinx.coroutines.delay
import org.json.JSONArray

data class Step(val action: String, val arg: String)

object AgentPlanner {
    private val allowed = setOf(
        "open_app", "youtube_search", "click_text", "scroll_down", "scroll_up",
        "type_text", "wait", "back", "home", "read_screen"
    )

    private const val SYSTEM =
        "You control an Android phone using ONLY these actions: open_app(name), youtube_search(query), " +
            "click_text(visible text), scroll_down, scroll_up, type_text(text), wait(seconds), back, home, read_screen. " +
            "Convert the user's goal into at most 10 steps. Reply with ONLY a JSON array like " +
            "[{\"action\":\"open_app\",\"arg\":\"YouTube\"}]. Never include payments, passwords, or destructive actions."

    suspend fun plan(app: MyraApplication, goal: String): Result<List<Step>> {
        val r = app.assistant.oneShot(goal, SYSTEM)
        if (r.isError) return Result.failure(Exception(r.text))
        return try {
            val t = r.text.replace(Regex("```[a-zA-Z]*"), "").replace("```", "").trim()
            val arr = JSONArray(t.substring(t.indexOf('['), t.lastIndexOf(']') + 1))
            val steps = (0 until arr.length()).mapNotNull {
                val o = arr.getJSONObject(it)
                val a = o.optString("action")
                if (a in allowed) Step(a, o.optString("arg")) else null
            }.take(10)
            if (steps.isEmpty()) Result.failure(Exception("कोई मान्य कदम नहीं मिला।")) else Result.success(steps)
        } catch (e: Exception) {
            Result.failure(Exception("Plan पढ़ नहीं सकी: ${e.message}"))
        }
    }

    suspend fun run(app: MyraApplication, steps: List<Step>) {
        Automation.stopped = false
        Automation.log("Plan शुरू (${steps.size} कदम)")
        for ((i, s) in steps.withIndex()) {
            if (Automation.stopped) { Automation.log("Plan रोका गया"); return }
            Automation.log("कदम ${i + 1}/${steps.size}: ${s.action} ${s.arg}")
            Actions.exec(app, s.action, s.arg)
            while (Automation.pending.value != null && !Automation.stopped) delay(300)
            delay(1200)
        }
        Automation.log("Plan पूरा")
    }
}
