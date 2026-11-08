package com.myra.ai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.ds by preferencesDataStore("myra_store")

/** All settings (including API keys) are AES-GCM encrypted with an Android Keystore key. */
private object KeyVault {
    private const val ALIAS = "myra_settings_key"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(enc: String): String = try {
        val raw = Base64.decode(enc, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)))
    } catch (e: Exception) {
        ""
    }
}

class SettingsStore(private val ctx: Context) {
    private val k = stringPreferencesKey("blob")

    val flow: Flow<MyraSettings> = ctx.ds.data.map { p ->
        val plain = p[k]?.let { KeyVault.decrypt(it) }.orEmpty()
        if (plain.isBlank()) MyraSettings()
        else runCatching { MyraSettings.fromJson(plain) }.getOrDefault(MyraSettings())
    }

    suspend fun save(s: MyraSettings) {
        ctx.ds.edit { it[k] = KeyVault.encrypt(s.toJson()) }
    }
}
