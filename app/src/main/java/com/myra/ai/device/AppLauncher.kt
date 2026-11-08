package com.myra.ai.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.myra.ai.automation.Automation

object AppLauncher {
    data class AppInfo(val label: String, val pkg: String)

    private val aliases = mapOf(
        "youtube" to "com.google.android.youtube", "यूट्यूब" to "com.google.android.youtube",
        "instagram" to "com.instagram.android", "इंस्टाग्राम" to "com.instagram.android",
        "whatsapp" to "com.whatsapp", "व्हाट्सएप" to "com.whatsapp", "व्हाट्सऐप" to "com.whatsapp",
        "chrome" to "com.android.chrome", "क्रोम" to "com.android.chrome"
    )

    fun installed(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Automation.log("launch failed: ${e.message}")
        false
    }

    /** Returns the label of the opened app, or null if not found / blocked. */
    fun open(ctx: Context, rawName: String): String? {
        val n = rawName.trim().lowercase().removeSuffix(" app").trim()
        if (n in setOf("camera", "कैमरा")) {
            return if (start(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) "Camera" else null
        }
        if (n in setOf("gallery", "गैलरी", "photos")) {
            val i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
            return if (start(ctx, i)) "Gallery" else null
        }
        if (n in setOf("settings", "सेटिंग", "सेटिंग्स")) {
            return if (start(ctx, Intent(Settings.ACTION_SETTINGS))) "Settings" else null
        }
        val apps = installed(ctx)
        val app = aliases[n]?.let { p -> apps.firstOrNull { it.pkg == p } }
            ?: apps.firstOrNull { it.label.lowercase() == n }
            ?: apps.firstOrNull { it.label.lowercase().startsWith(n) }
            ?: apps.firstOrNull { it.label.lowercase().contains(n) }
            ?: return null
        val launch = ctx.packageManager.getLaunchIntentForPackage(app.pkg) ?: return null
        return if (start(ctx, launch)) app.label else null
    }

    fun youtubeSearch(ctx: Context, q: String) =
        viewUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q))

    fun youtubeVideo(ctx: Context, id: String) = viewUrl(ctx, "https://www.youtube.com/watch?v=$id")

    private fun viewUrl(ctx: Context, url: String): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val yt = Intent(i).setPackage("com.google.android.youtube")
        return start(ctx, yt) || start(ctx, Intent(i))
    }
}
