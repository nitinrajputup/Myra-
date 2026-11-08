package com.myra.ai.data

import org.json.JSONObject

data class MyraSettings(
    val apiKey: String = "",
    val model: String = "gemini-2.5-flash",
    val instructions: String = "",
    val personality: String = "Friendly",
    val responseStyle: String = "Concise",
    val appLanguage: String = "Auto",
    val listenLang: String = "hi-IN",
    val memoryEnabled: Boolean = true,
    val webSearch: Boolean = false,
    val speakReplies: Boolean = true,
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.1f,
    val voiceVolume: Float = 1.0f,
    val voiceName: String = "",
    val wakeEnabled: Boolean = false,
    val continuous: Boolean = false,
    val allowInterrupt: Boolean = true,
    val replyHello: String = "जी बॉस, मैं सुन रही हूँ।",
    val replyMyra: String = "जी, बताइए।",
    val chargeAnn: Boolean = true,
    val lowAnn: Boolean = true,
    val fullAnn: Boolean = true,
    val lowThreshold: Int = 20,
    val msgCharging: String = "बॉस, फोन चार्जिंग पर लगा दिया गया है।",
    val msgLow: String = "बॉस, बैटरी कम हो रही है।",
    val msgFull: String = "बॉस, बैटरी पूरी चार्ज हो गई है।",
    val ytKey: String = "",
    val confirmSensitive: Boolean = true,
    val accent: String = "Purple"
) {
    fun toJson(): String = JSONObject().apply {
        put("apiKey", apiKey); put("model", model); put("instructions", instructions)
        put("personality", personality); put("responseStyle", responseStyle)
        put("appLanguage", appLanguage); put("listenLang", listenLang)
        put("memoryEnabled", memoryEnabled); put("webSearch", webSearch)
        put("speakReplies", speakReplies)
        put("voiceSpeed", voiceSpeed.toDouble()); put("voicePitch", voicePitch.toDouble())
        put("voiceVolume", voiceVolume.toDouble()); put("voiceName", voiceName)
        put("wakeEnabled", wakeEnabled); put("continuous", continuous)
        put("allowInterrupt", allowInterrupt)
        put("replyHello", replyHello); put("replyMyra", replyMyra)
        put("chargeAnn", chargeAnn); put("lowAnn", lowAnn); put("fullAnn", fullAnn)
        put("lowThreshold", lowThreshold)
        put("msgCharging", msgCharging); put("msgLow", msgLow); put("msgFull", msgFull)
        put("ytKey", ytKey); put("confirmSensitive", confirmSensitive); put("accent", accent)
    }.toString()

    companion object {
        const val DEFAULT_INSTRUCTIONS =
            "Be warm, helpful and accurate. Keep answers short unless the user asks for detail."

        fun fromJson(s: String): MyraSettings {
            val j = JSONObject(s)
            val d = MyraSettings()
            return MyraSettings(
                apiKey = j.optString("apiKey", d.apiKey),
                model = j.optString("model", d.model),
                instructions = j.optString("instructions", d.instructions),
                personality = j.optString("personality", d.personality),
                responseStyle = j.optString("responseStyle", d.responseStyle),
                appLanguage = j.optString("appLanguage", d.appLanguage),
                listenLang = j.optString("listenLang", d.listenLang),
                memoryEnabled = j.optBoolean("memoryEnabled", d.memoryEnabled),
                webSearch = j.optBoolean("webSearch", d.webSearch),
                speakReplies = j.optBoolean("speakReplies", d.speakReplies),
                voiceSpeed = j.optDouble("voiceSpeed", d.voiceSpeed.toDouble()).toFloat(),
                voicePitch = j.optDouble("voicePitch", d.voicePitch.toDouble()).toFloat(),
                voiceVolume = j.optDouble("voiceVolume", d.voiceVolume.toDouble()).toFloat(),
                voiceName = j.optString("voiceName", d.voiceName),
                wakeEnabled = j.optBoolean("wakeEnabled", d.wakeEnabled),
                continuous = j.optBoolean("continuous", d.continuous),
                allowInterrupt = j.optBoolean("allowInterrupt", d.allowInterrupt),
                replyHello = j.optString("replyHello", d.replyHello),
                replyMyra = j.optString("replyMyra", d.replyMyra),
                chargeAnn = j.optBoolean("chargeAnn", d.chargeAnn),
                lowAnn = j.optBoolean("lowAnn", d.lowAnn),
                fullAnn = j.optBoolean("fullAnn", d.fullAnn),
                lowThreshold = j.optInt("lowThreshold", d.lowThreshold),
                msgCharging = j.optString("msgCharging", d.msgCharging),
                msgLow = j.optString("msgLow", d.msgLow),
                msgFull = j.optString("msgFull", d.msgFull),
                ytKey = j.optString("ytKey", d.ytKey),
                confirmSensitive = j.optBoolean("confirmSensitive", d.confirmSensitive),
                accent = j.optString("accent", d.accent)
            )
        }
    }
}
