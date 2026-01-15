package org.css_apps_m3.password_manager.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.css_apps_m3.password_manager.model.PasswordEntry
import java.io.File
import java.io.OutputStreamWriter

class PasswordRepository(private val context: Context) {
    private val gson = Gson()

    // MasterKey in the Android Keystore with AES256
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    // internal encrypted file
    private val file = File(context.filesDir, "passwords.json.enc")

    fun hasLocalData(): Boolean = file.exists()

    fun saveLocal(list: List<PasswordEntry>) {
        val json = gson.toJson(list)

        // Ensure we always overwrite by deleting the old file
        if (file.exists()) {
            file.delete()
        }

        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        encryptedFile.openFileOutput().use { output ->
            output.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    fun loadPasswords(): List<PasswordEntry> {
        if (!file.exists()) return emptyList()

        val encryptedFile = EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val json = encryptedFile.openFileInput().use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
        val type = object : TypeToken<List<PasswordEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    /**
     * Update a password entry by matching domain + username.
     * If not found, it will not change anything.
     */
    fun updatePassword(updatedEntry: PasswordEntry) {
        val passwords = loadPasswords().toMutableList()
        val index = passwords.indexOfFirst {
            it.url == updatedEntry.url && it.username == updatedEntry.username
        }

        if (index != -1) {
            passwords[index] = updatedEntry
            saveLocal(passwords)
        }
    }

    fun deletePassword(entryToDelete: PasswordEntry) {
        val passwords = loadPasswords().toMutableList()
        // Remove the entry. We assume the data class PasswordEntry implements equals() correctly
        val wasRemoved = passwords.remove(entryToDelete)

        // If simple removal fails, you can try matching manually:
        // passwords.removeAll { it.url == entryToDelete.url && it.username == entryToDelete.username }

        if (wasRemoved) {
            saveLocal(passwords)
        }
    }

    fun exportPasswordsToCsv(
        context: Context,
        uri: android.net.Uri
    ): Int {
        val passwords = loadPasswords() // Same source as UI

        if (passwords.isEmpty()) return 0

        context.contentResolver.openOutputStream(uri)?.use { out ->
            OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                writer.appendLine("name,url,username,password,note")

                passwords.forEach { p ->
                    fun esc(value: String?): String =
                        value
                            ?.replace("\"", "\"\"")
                            ?.replace("\n", " ")
                            ?.let { "\"$it\"" }
                            ?: "\"\""

                    writer.appendLine(
                        listOf(
                            esc(p.name),
                            esc(p.url),
                            esc(p.username),
                            esc(p.password),
                            esc(p.note)
                        ).joinToString(",")
                    )
                }
            }
        }
        return passwords.size
    }

}
