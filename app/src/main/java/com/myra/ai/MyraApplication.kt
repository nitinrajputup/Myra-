package com.myra.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.myra.ai.core.Assistant
import com.myra.ai.data.MyraDb
import com.myra.ai.data.MyraSettings
import com.myra.ai.data.SettingsStore
import com.myra.ai.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MyraApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: MyraDb
    lateinit var store: SettingsStore
    lateinit var settings: StateFlow<MyraSettings>
    lateinit var speaker: Speaker
    lateinit var assistant: Assistant

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, MyraDb::class.java, "myra.db").fallbackToDestructiveMigration().build()
        store = SettingsStore(this)
        settings = store.flow.stateIn(scope, SharingStarted.Eagerly, MyraSettings())
        speaker = Speaker(this)
        assistant = Assistant(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("service", "MYRA background", NotificationManager.IMPORTANCE_LOW))
        scope.launch { settings.collect { speaker.apply(it) } }
    }
}
