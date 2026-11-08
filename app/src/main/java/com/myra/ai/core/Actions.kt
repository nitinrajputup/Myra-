package com.myra.ai.core

import com.myra.ai.MyraApplication
import com.myra.ai.automation.Automation
import com.myra.ai.automation.MyraAccessibilityService
import com.myra.ai.device.AppLauncher
import kotlinx.coroutines.delay

/** Every phone action goes through here: logged, stoppable, and confirmed when sensitive. */
object Actions {
    private val sensitive = Regex(
        "pay|send|delete|buy|order|transfer|confirm|purchase|submit|password|upi|पेमेंट|भेज|डिलीट|खरीद|सबमिट|पासवर्ड|ट्रांसफर",
        RegexOption.IGNORE_CASE
    )
    const val NO_A11Y = "Accessibility सेवा चालू नहीं है। Settings → Accessibility में MYRA चालू करें।"

    suspend fun exec(app: MyraApplication, name: String, arg: String = ""): String {
        if (Automation.stopped) return "Automation रुका हुआ है।"
        Automation.status.value = "Running: $name $arg".trim()
        Automation.log("▶ $name $arg".trim())
        val out = try {
            run(app, name, arg)
        } catch (e: Exception) {
            "त्रुटि: ${e.message}"
        }
        Automation.log("◀ $out")
        if (!Automation.stopped) Automation.status.value = "Idle"
        return out
    }

    private fun guarded(app: MyraApplication, desc: String, needs: Boolean, run: () -> String): String {
        if (needs && app.settings.value.confirmSensitive) {
            Automation.pending.value = Automation.Pending(desc, run)
            return "यह संवेदनशील कार्य है — ऐप में Confirm दबाइए।"
        }
        return run()
    }

    private suspend fun run(app: MyraApplication, name: String, arg: String): String {
        val svc = MyraAccessibilityService.instance
        return when (name) {
            "open_app" -> AppLauncher.open(app, arg)?.let { "$it खोल दिया।" } ?: "\"$arg\" नहीं खुल सका।"
            "youtube_search" -> {
                if (AppLauncher.youtubeSearch(app, arg)) "YouTube पर \"$arg\" खोज रही हूँ।" else "YouTube नहीं खुल सका।"
            }
            "wait" -> {
                delay(((arg.toDoubleOrNull() ?: 1.0) * 1000).toLong())
                "प्रतीक्षा पूरी।"
            }
            "scroll_down", "scroll_up" -> {
                if (svc == null) NO_A11Y
                else if (svc.scroll(name == "scroll_down")) "स्क्रॉल कर दिया।" else "स्क्रॉल नहीं हो सका।"
            }
            "click_text" -> {
                if (svc == null) NO_A11Y
                else guarded(app, "\"$arg\" पर क्लिक करें?", sensitive.containsMatchIn(arg)) {
                    if (svc.clickText(arg)) "\"$arg\" पर क्लिक किया।" else "\"$arg\" स्क्रीन पर नहीं मिला।"
                }
            }
            "type_text" -> {
                if (svc == null) NO_A11Y
                else guarded(app, "यह टेक्स्ट लिखें: \"$arg\"?", sensitive.containsMatchIn(arg)) {
                    if (svc.typeText(arg)) "टेक्स्ट लिख दिया।" else "कोई टेक्स्ट फ़ील्ड चुना हुआ नहीं है (या पासवर्ड फ़ील्ड है)।"
                }
            }
            "tap" -> {
                val p = arg.split(",").mapNotNull { it.trim().toFloatOrNull() }
                if (svc == null) NO_A11Y
                else if (p.size != 2) "tap के लिए x,y दें।"
                else if (svc.tap(p[0], p[1])) "टैप किया।" else "टैप नहीं हो सका।"
            }
            "swipe" -> {
                val p = arg.split(",").mapNotNull { it.trim().toFloatOrNull() }
                if (svc == null) NO_A11Y
                else if (p.size != 4) "swipe के लिए x1,y1,x2,y2 दें।"
                else if (svc.swipe(p[0], p[1], p[2], p[3], 350)) "स्वाइप किया।" else "स्वाइप नहीं हो सका।"
            }
            "back" -> if (svc == null) NO_A11Y else { svc.back(); "पीछे गई।" }
            "home" -> if (svc == null) NO_A11Y else { svc.home(); "होम पर आ गई।" }
            "read_screen" -> {
                if (svc == null) NO_A11Y
                else svc.screenText().ifBlank { "स्क्रीन पर पढ़ने लायक टेक्स्ट नहीं मिला।" }.take(1500)
            }
            else -> "अज्ञात कार्य: $name"
        }
    }
}
