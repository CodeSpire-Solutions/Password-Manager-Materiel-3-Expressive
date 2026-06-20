package org.css_apps_m3.password_manager.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import org.css_apps_m3.password_manager.R
import org.css_apps_m3.password_manager.model.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordDetailScreen(
    domain: String,
    accounts: List<PasswordEntry>,
    onBack: () -> Unit,
    onEdit: (PasswordEntry) -> Unit
) {
    val context = LocalContext.current
    BlockScreenshotsOnThisScreen(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(domain) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val first = accounts.firstOrNull()
                    if (first != null) {
                        IconButton(onClick = { onEdit(first) }) {
                            Icon(
                                painter = painterResource(id = R.drawable.pencil),
                                contentDescription = "Edit"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            accounts.forEach { entry ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailCard(label = "Username", value = entry.username, context = context)
                        PasswordCard(label = "Password", value = entry.password, context = context)
                        if (!entry.note.isNullOrBlank()) {
                            DetailCard(label = "Note", value = entry.note!!, context = context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockScreenshotsOnThisScreen(context: Context) {
    val window = remember(context) { context.findActivityWindow() }

    DisposableEffect(window) {
        val previousSecureState =
            window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0

        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        onDispose {
            if (previousSecureState) {
                window?.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

private tailrec fun Context.findActivityWindow(): Window? = when (this) {
    is FragmentActivity -> window
    is android.content.ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}


@Composable
fun DetailCard(label: String, value: String, context: Context) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = {
                copyToClipboard(context, label, value)
            }) {
                Icon(painter = painterResource(id = R.drawable.content_copy), contentDescription = "Copy")
            }
        }
    }
}

@Composable
fun PasswordCard(label: String, value: String, context: Context) {
    var visible by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (visible) value else "••••••••",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Row {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        painter = painterResource(
                            id = if (visible) R.drawable.visibility else R.drawable.visibilityoff
                        ),
                        contentDescription = if (visible) "Hide Password" else "Show Password"
                    )
                }
                IconButton(onClick = {
                    copyToClipboard(context, label, value)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.content_copy),
                        contentDescription = "Copy"
                    )
                }
            }
        }
    }
}


private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}
