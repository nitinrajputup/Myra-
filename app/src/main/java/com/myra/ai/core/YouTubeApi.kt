package com.myra.ai.core

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

object YouTubeApi {
    data class Video(val id: String, val title: String, val channel: String)

    private val http = OkHttpClient()

    /** Needs the user's own YouTube Data API v3 key (Settings). */
    suspend fun search(key: String, q: String): Result<List<Video>> = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result.failure<List<Video>>(IOException("Settings में YouTube Data API key डालें।"))
        try {
            val url = "https://www.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
                .addQueryParameter("part", "snippet").addQueryParameter("type", "video")
                .addQueryParameter("maxResults", "15").addQueryParameter("q", q)
                .addQueryParameter("key", key).build()
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val m = runCatching { JSONObject(raw).getJSONObject("error").getString("message") }.getOrDefault(raw.take(150))
                    Result.failure<List<Video>>(IOException("YouTube ${r.code}: $m"))
                } else {
                    val items = JSONObject(raw).optJSONArray("items")
                    val list = ArrayList<Video>()
                    if (items != null) for (i in 0 until items.length()) {
                        val o = items.getJSONObject(i)
                        val sn = o.getJSONObject("snippet")
                        list.add(
                            Video(
                                o.getJSONObject("id").getString("videoId"),
                                Html.fromHtml(sn.getString("title"), Html.FROM_HTML_MODE_LEGACY).toString(),
                                sn.optString("channelTitle")
                            )
                        )
                    }
                    Result.success(list)
                }
            }
        } catch (e: Exception) {
            Result.failure<List<Video>>(IOException("Network error: ${e.message}"))
        }
    }
}
