package com.myra.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import com.myra.ai.ui.MyraRoot
import com.myra.ai.ui.theme.MyraTheme
import com.myra.ai.ui.theme.accentFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MyraApplication
        setContent {
            val s by app.settings.collectAsState()
            MyraTheme(accentFor(s.accent)) { MyraRoot() }
        }
    }
}
