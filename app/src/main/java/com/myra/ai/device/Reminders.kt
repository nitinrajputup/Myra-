package com.myra.ai.device

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: "Reminder"
        val n = NotificationCompat.Builder(applicationContext, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("MYRA Reminder")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify((System.currentTimeMillis() % 100000).toInt(), n)
        return Result.success()
    }
}

object Reminders {
    fun schedule(ctx: Context, minutes: Long, text: String) {
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .setInputData(workDataOf("text" to text))
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
    }

    /** Opens the calendar app's "new event" screen prefilled; the user confirms and saves. */
    fun calendar(ctx: Context, title: String) {
        val i = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }
}
