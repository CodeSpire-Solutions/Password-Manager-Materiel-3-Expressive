package org.css_apps_m3.password_manager.util

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.css_apps_m3.password_manager.model.CustomField
import org.css_apps_m3.password_manager.model.PasswordEntry
import java.io.InputStreamReader

object CsvReader {
    private val gson = Gson()
    private val customFieldsType = object : TypeToken<List<CustomField>>() {}.type

    fun readPasswordsFromUri(context: Context, uri: Uri): List<PasswordEntry> {
        val entries = mutableListOf<PasswordEntry>()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        val reader = CSVReader(InputStreamReader(inputStream))

        reader.use { csvReader ->
            csvReader.readNext() // Skip header
            var line: Array<String>?
            while (csvReader.readNext().also { line = it } != null) {
                line?.let {
                    if (it.size >= 4) {
                        entries.add(
                            PasswordEntry(
                                name = it[0],
                                url = it[1],
                                username = it[2],
                                password = it[3],
                                // Safe access: Only read 'note' if the column actually exists
                                note = if (it.size > 4) it[4] else "",
                                customFields = if (it.size > 5) {
                                    runCatching {
                                        gson.fromJson<List<CustomField>>(it[5], customFieldsType)
                                    }.getOrNull() ?: emptyList()
                                } else {
                                    emptyList()
                                }
                            )
                        )
                    }
                }
            }
        }
        return entries
    }
}
