package com.myra.ai.coding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectStore(private val ctx: Context) {
    val root = File(ctx.filesDir, "projects").apply { mkdirs() }
    private val skip = setOf("build", ".gradle", ".idea")

    fun valid(name: String) = name.matches(Regex("[A-Za-z0-9_-]{1,40}"))
    fun dir(p: String) = File(root, p)
    fun projects(): List<String> = root.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun create(p: String, files: Map<String, String> = emptyMap()) {
        require(valid(p)) { "नाम में सिर्फ़ अक्षर, अंक, - और _ रखें" }
        dir(p).mkdirs()
        files.forEach { (rel, text) -> write(p, rel, text) }
        if (files.isEmpty()) write(p, "README.md", "# $p\n")
    }

    private fun fileOf(p: String, rel: String): File {
        val d = dir(p).canonicalFile
        val f = File(d, rel).canonicalFile
        require(f.path.startsWith(d.path + File.separator) || f == d) { "invalid path" }
        return f
    }

    fun files(p: String): List<String> {
        val d = dir(p)
        return d.walkTopDown().onEnter { it == d || it.name !in skip }
            .filter { it.isFile }.map { it.relativeTo(d).path }.sorted().toList()
    }

    fun read(p: String, rel: String): String = fileOf(p, rel).readText()
    fun write(p: String, rel: String, text: String) {
        val f = fileOf(p, rel)
        f.parentFile?.mkdirs()
        f.writeText(text)
    }
    fun delete(p: String, rel: String) { fileOf(p, rel).delete() }
    fun deleteProject(p: String) { dir(p).deleteRecursively() }

    fun zip(p: String): File {
        val d = dir(p)
        val out = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "$p.zip")
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            files(p).forEach { rel ->
                z.putNextEntry(ZipEntry(rel.replace('\\', '/')))
                File(d, rel).inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return out
    }

    fun apk(p: String): File? =
        File(dir(p), "app/build/outputs/apk/debug/app-debug.apk").takeIf { it.exists() }

    fun importZip(uri: Uri, name: String) {
        require(valid(name)) { "नाम में सिर्फ़ अक्षर, अंक, - और _ रखें" }
        val base = dir(name).apply { mkdirs() }.canonicalFile
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val t = File(base, e.name).canonicalFile
                    if (t.path.startsWith(base.path + File.separator)) {
                        if (e.isDirectory) t.mkdirs() else {
                            t.parentFile?.mkdirs()
                            t.outputStream().use { z.copyTo(it) }
                        }
                    }
                    e = z.nextEntry
                }
            }
        }
    }

    companion object {
        fun share(ctx: Context, f: File, mime: String) {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
            val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "Export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
