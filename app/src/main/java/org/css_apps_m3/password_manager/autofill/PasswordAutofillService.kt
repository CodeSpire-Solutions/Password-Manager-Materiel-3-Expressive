package org.css_apps_m3.password_manager.autofill

import android.app.assist.AssistStructure
import android.net.Uri
import android.os.Build
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.service.autofill.Presentations
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import org.css_apps_m3.password_manager.R
import org.css_apps_m3.password_manager.data.PasswordRepository
import org.css_apps_m3.password_manager.data.SqlSyncManager
import org.css_apps_m3.password_manager.model.PasswordEntry
import org.css_apps_m3.password_manager.util.PasswordGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class PasswordAutofillService : AutofillService() {

    private val repository by lazy { PasswordRepository(this) }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: android.os.CancellationSignal,
        callback: FillCallback
    ) {
        val context = request.fillContexts.lastOrNull() ?: run {
            callback.onSuccess(null)
            return
        }
        val structure = context.structure
        val parsed = parseStructure(structure)
        if (parsed.passwordId == null && parsed.usernameId == null) {
            callback.onSuccess(null)
            return
        }

        val allEntries = repository.loadPasswords()
        val candidates = if (parsed.domain.isBlank()) {
            allEntries.take(5)
        } else {
            allEntries.filter { matchesDomain(it.url, parsed.domain) }.ifEmpty { allEntries.take(5) }
        }

        val responseBuilder = FillResponse.Builder()
            .setSaveInfo(
                SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                    listOfNotNull(parsed.usernameId, parsed.passwordId).toTypedArray()
                )
                    .setDescription("Save password to Password Manager")
                    .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
                    .build()
            )

        candidates.forEach { entry ->
            responseBuilder.addDataset(buildDataset(entry, parsed.usernameId, parsed.passwordId))
        }
        if (parsed.isRegistration && parsed.passwordId != null) {
            responseBuilder.addDataset(buildGeneratedPasswordDataset(parsed.passwordId))
        }
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess()
            return
        }

        val parsed = parseStructure(structure)
        val username = parsed.usernameValue.trim()
        val password = parsed.passwordValue.trim()
        if (username.isBlank() || password.isBlank()) {
            callback.onSuccess()
            return
        }

        val domain = parsed.domain.ifBlank { "Autofill Entry" }
        val existing = repository.loadPasswords().toMutableList()
        val duplicateIndex = existing.indexOfFirst {
            it.username.equals(username, ignoreCase = true) && normalizeDomain(it.url) == normalizeDomain(domain)
        }
        val newEntry = PasswordEntry(
            name = domain,
            url = domain,
            username = username,
            password = password
        )
        if (duplicateIndex >= 0) {
            existing[duplicateIndex] = newEntry
        } else {
            existing.add(newEntry)
        }
        repository.saveLocal(existing)
        val syncConfig = SqlSyncManager.loadConfig(this)
        if (syncConfig.autoSync) {
            CoroutineScope(Dispatchers.IO).launch {
                SqlSyncManager(this@PasswordAutofillService).sync(syncConfig, existing)
            }
        }
        callback.onSuccess()
    }

    private fun buildDataset(
        entry: PasswordEntry,
        usernameId: AutofillId?,
        passwordId: AutofillId?
    ): Dataset {
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, entry.name.ifBlank { normalizeDomain(entry.url) })
            setTextViewText(android.R.id.text2, entry.username)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            buildModernDataset(
                presentation = presentation,
                usernameId = usernameId,
                username = entry.username,
                passwordId = passwordId,
                password = entry.password
            )
        } else {
            buildLegacyDataset(presentation, usernameId, entry.username, passwordId, entry.password)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun buildModernDataset(
        presentation: RemoteViews,
        usernameId: AutofillId?,
        username: String,
        passwordId: AutofillId?,
        password: String
    ): Dataset {
        val presentations = Presentations.Builder()
            .setMenuPresentation(presentation)
            .build()
        val builder = Dataset.Builder(presentations)
        if (usernameId != null) {
            builder.setField(usernameId, Field.Builder().setValue(AutofillValue.forText(username)).build())
        }
        if (passwordId != null) {
            builder.setField(passwordId, Field.Builder().setValue(AutofillValue.forText(password)).build())
        }
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun buildLegacyDataset(
        presentation: RemoteViews,
        usernameId: AutofillId?,
        username: String,
        passwordId: AutofillId?,
        password: String
    ): Dataset {
        val builder = Dataset.Builder(presentation)
        usernameId?.let { builder.setValue(it, AutofillValue.forText(username)) }
        passwordId?.let { builder.setValue(it, AutofillValue.forText(password)) }
        return builder.build()
    }

    private fun buildGeneratedPasswordDataset(passwordId: AutofillId?): Dataset {
        val generatedPassword = PasswordGenerator.generate()
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_2).apply {
            setTextViewText(android.R.id.text1, "Generate strong password")
            setTextViewText(android.R.id.text2, "A new 20-character password")
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            buildModernDataset(presentation, null, "", passwordId, generatedPassword)
        } else {
            buildLegacyDataset(presentation, null, "", passwordId, generatedPassword)
        }
    }

    private fun parseStructure(structure: AssistStructure): ParsedAutofillFields {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var usernameValue = ""
        var passwordValue = ""
        var domain = ""
        var isRegistration = false

        for (windowIndex in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(windowIndex)
            val rootNode = windowNode.rootViewNode ?: continue
            val webDomain = rootNode.webDomain
            if (!webDomain.isNullOrBlank()) {
                domain = webDomain
            }
            traverseNode(rootNode) { node ->
                val hint = node.hint?.lowercase(Locale.ROOT).orEmpty()
                val idEntry = node.idEntry?.lowercase(Locale.ROOT).orEmpty()
                val web = node.webDomain?.lowercase(Locale.ROOT).orEmpty()
                if (web.isNotBlank()) domain = web
                val inputType = node.inputType
                val htmlAttrs = htmlAttributes(node)
                val autoComplete = htmlAttrs["autocomplete"].orEmpty()
                val htmlType = htmlAttrs["type"].orEmpty()
                val isPasswordInput = (inputType and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0 ||
                    (inputType and android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) != 0 ||
                    hint.contains("password") || idEntry.contains("password") || idEntry.contains("pass") ||
                    htmlType == "password" || autoComplete.contains("password")
                val isNewPassword = autoComplete.contains("new-password") ||
                    hint.contains("new password") || idEntry.contains("new_password") ||
                    hint.contains("create password") || idEntry.contains("create_password") ||
                    node.autofillHints?.any {
                        it.equals("newPassword", ignoreCase = true) ||
                            it.equals("new-password", ignoreCase = true)
                    } == true
                if (isNewPassword) isRegistration = true
                val isUserInput = hint.contains("user") || hint.contains("email") || hint.contains("login") ||
                    idEntry.contains("name") ||
                    idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login")
                    || autoComplete.contains("username") || autoComplete.contains("email")

                if (passwordId == null && isPasswordInput && node.autofillId != null) {
                    passwordId = node.autofillId
                    passwordValue = node.autofillValue?.textValue?.toString().orEmpty()
                } else if (usernameId == null && isUserInput && node.autofillId != null) {
                    usernameId = node.autofillId
                    usernameValue = node.autofillValue?.textValue?.toString().orEmpty()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (usernameId == null && node.autofillHints?.any {
                            it.contains(View.AUTOFILL_HINT_USERNAME, true) || it.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS, true)
                        } == true) {
                        usernameId = node.autofillId
                        usernameValue = node.autofillValue?.textValue?.toString().orEmpty()
                    }
                    if (passwordId == null && node.autofillHints?.any {
                            it.contains(View.AUTOFILL_HINT_PASSWORD, true)
                        } == true) {
                        passwordId = node.autofillId
                        passwordValue = node.autofillValue?.textValue?.toString().orEmpty()
                    }
                }
            }
        }

        return ParsedAutofillFields(
            usernameId = usernameId,
            passwordId = passwordId,
            usernameValue = usernameValue,
            passwordValue = passwordValue,
            domain = normalizeDomain(domain),
            isRegistration = isRegistration
        )
    }

    private fun traverseNode(
        node: AssistStructure.ViewNode,
        block: (AssistStructure.ViewNode) -> Unit
    ) {
        block(node)
        for (i in 0 until node.childCount) {
            traverseNode(node.getChildAt(i), block)
        }
    }

    private fun matchesDomain(url: String, domain: String): Boolean {
        val left = normalizeDomain(url)
        val right = normalizeDomain(domain)
        return left == right || left.endsWith(".$right") || right.endsWith(".$left")
    }

    private fun normalizeDomain(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val raw = if (value.contains("://")) value else "https://$value"
            val host = Uri.parse(raw).host ?: value
            host.removePrefix("www.").lowercase(Locale.ROOT).trim()
        } catch (_: Exception) {
            value.removePrefix("www.").lowercase(Locale.ROOT).trim()
        }
    }

    private fun htmlAttributes(node: AssistStructure.ViewNode): Map<String, String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyMap()
        val htmlInfo = node.htmlInfo ?: return emptyMap()
        val attrs = htmlInfo.attributes ?: return emptyMap()
        return attrs.associate { it.first.lowercase(Locale.ROOT) to it.second.lowercase(Locale.ROOT) }
    }
}

private data class ParsedAutofillFields(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val usernameValue: String,
    val passwordValue: String,
    val domain: String,
    val isRegistration: Boolean
)
