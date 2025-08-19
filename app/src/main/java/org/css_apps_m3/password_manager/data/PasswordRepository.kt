package org.css_apps_m3.password_manager.data

import android.content.Context
import android.os.Environment
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.css_apps_m3.password_manager.model.PasswordEntry
import java.io.File

class PasswordRepository(private val context: Context) {
    private val gson = Gson()

    // MasterKey in the Android Keystore with AES256
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // internal encrypted file
    private val file = File(context.filesDir, "passwords.json.enc")

    private val encryptedFile = EncryptedFile.Builder(
        context,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    fun hasLocalData(): Boolean = file.exists()

    fun saveLocal(list: List<PasswordEntry>) {
        val json = gson.toJson(list)

        // Stores encrypted internally
        encryptedFile.openFileOutput().use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    fun loadPasswords(): List<PasswordEntry> {
        if (!file.exists()) return emptyList()
        val json = encryptedFile.openFileInput().use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
        val type = object : TypeToken<List<PasswordEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}