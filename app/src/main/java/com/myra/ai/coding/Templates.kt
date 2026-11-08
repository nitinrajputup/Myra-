package com.myra.ai.coding

object Templates {
    val languages = listOf("Kotlin", "Java", "Python", "JavaScript", "HTML", "CSS", "C", "C++", "SQL", "JSON", "XML", "Other")
    fun ext(lang: String) = when (lang) {
        "Kotlin" -> "kt"; "Java" -> "java"; "Python" -> "py"; "JavaScript" -> "js"; "HTML" -> "html"
        "CSS" -> "css"; "C" -> "c"; "C++" -> "cpp"; "SQL" -> "sql"; "JSON" -> "json"; "XML" -> "xml"; else -> "txt"
    }

    /** Minimal, buildable Jetpack Compose Android project. */
    fun androidProject(name: String): Map<String, String> {
        val id = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "app" }
        val pkg = "com.example.$id"
        val dir = "app/src/main/java/" + pkg.replace('.', '/')
        return mapOf(
            "settings.gradle.kts" to """
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "$name"
include(":app")
""".trimStart(),
            "build.gradle.kts" to """
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
""".trimStart(),
            "gradle.properties" to "org.gradle.jvmargs=-Xmx1536m\nandroid.useAndroidX=true\nandroid.nonTransitiveRClass=true\n",
            "app/build.gradle.kts" to """
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "$pkg"
    compileSdk = 34
    defaultConfig { applicationId = "$pkg"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
}
""".trimStart(),
            "app/src/main/AndroidManifest.xml" to """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="$name" android:theme="@style/Theme.App">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""".trimStart(),
            "app/src/main/res/values/themes.xml" to """
<resources>
    <style name="Theme.App" parent="android:Theme.Material.NoActionBar" />
</resources>
""".trimStart(),
            "$dir/MainActivity.kt" to """
package $pkg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Hello from $name") } }
    }
}
""".trimStart()
        )
    }
}
