package com.myra.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Attach(val mime: String, val b64: String, val name: String)
data class ChatTurn(val fromUser: Boolean, val text: String, val attach: Attach? = null)

class GeminiClient {
    private val http = OkHttpClient.Builder().callTimeout(90, TimeUnit.SECONDS).build()

    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        history: List<ChatTurn>,
        webSearch: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure<String>(IOException("Gemini API key नहीं है। Settings में key डालें।"))
        }
        try {
            val contents = JSONArray()
            history.forEach { t ->
                val parts = JSONArray()
                t.attach?.let {
                    parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", it.mime).put("data", it.b64)))
                }
                parts.put(JSONObject().put("text", t.text.ifBlank { "(attachment)" }))
                contents.put(JSONObject().put("role", if (t.fromUser) "user" else "model").put("parts", parts))
            }
            val body = JSONObject()
                .put("contents", contents)
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            if (webSearch) {
                body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
            val req = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }
                        .getOrDefault(raw.take(200))
                    Result.failure<String>(IOException("Gemini ${resp.code}: $msg"))
                } else {
                    parse(raw)
                }
            }
        } catch (e: IOException) {
            Result.failure<String>(IOException("Network error: ${e.message}"))
        } catch (e: Exception) {
            Result.failure<String>(IOException("Unexpected error: ${e.message}"))
        }
    }

    private fun parse(raw: String): Result<String> {
        val j = JSONObject(raw)
        val c = j.optJSONArray("candidates")
        if (c == null || c.length() == 0) {
            val reason = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return Result.failure(IOException("कोई उत्तर नहीं मिला. $reason"))
        }
        val parts = c.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        val out = sb.toString().trim()
        return if (out.isEmpty()) Result.failure(IOException("खाली उत्तर मिला।")) else Result.success(out)
    }
}
