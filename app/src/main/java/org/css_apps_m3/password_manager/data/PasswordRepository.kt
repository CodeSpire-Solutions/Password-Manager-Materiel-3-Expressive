package org.css_apps_m3.password_manager.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.css_apps_m3.password_manager.model.PasswordEntry
import java.io.File

class PasswordRepository(private val context: Context) {
    private val gson = Gson()
    private val file = File(context.filesDir, "passwords.json")

    fun hasLocalData(): Boolean = file.exists()

    fun saveLocal(list: List<PasswordEntry>) {
        file.writeText(gson.toJson(list))
    }

    fun loadPasswords(): List<PasswordEntry> {
        if (!file.exists()) return emptyList()
        val type = object : TypeToken<List<PasswordEntry>>() {}.type
        return gson.fromJson(file.readText(), type)
    }
}
