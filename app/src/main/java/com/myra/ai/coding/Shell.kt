package com.myra.ai.coding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/** Runs real shell commands inside the app sandbox (limited by Android; Gradle usually needs Termux). */
object Shell {
    fun run(cmd: String, dir: File): Flow<String> = flow {
        val p = ProcessBuilder("sh", "-c", cmd).directory(dir).redirectErrorStream(true).start()
        try {
            val r = p.inputStream.bufferedReader()
            while (true) {
                val l = r.readLine() ?: break
                emit(l)
            }
            emit("[exit ${p.waitFor()}]")
        } finally {
            p.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
