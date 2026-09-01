package org.css_apps_m3.password_manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.css_apps_m3.password_manager.model.CustomField

@Composable
fun CustomFieldsEditor(
    customFields: List<CustomField>,
    onCustomFieldsChange: (List<CustomField>) -> Unit,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Custom fields", style = MaterialTheme.typography.titleSmall)

        customFields.forEachIndexed { index, field ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = field.label,
                        onValueChange = { value ->
                            onCustomFieldsChange(customFields.updated(index, field.copy(label = value)))
                        },
                        label = { Text("Field name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = enabled
                    )
                    OutlinedTextField(
                        value = field.value,
                        onValueChange = { value ->
                            onCustomFieldsChange(customFields.updated(index, field.copy(value = value)))
                        },
                        label = { Text("Value") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled
                    )
                }
                IconButton(
                    onClick = {
                        onCustomFieldsChange(customFields.filterIndexed { currentIndex, _ -> currentIndex != index })
                    },
                    enabled = enabled
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove custom field")
                }
            }
        }

        TextButton(
            onClick = { onCustomFieldsChange(customFields + CustomField(label = "", value = "")) },
            enabled = enabled
        ) {
            Text("Add custom field")
        }
    }
}

private fun List<CustomField>.updated(index: Int, field: CustomField): List<CustomField> =
    toMutableList().apply { this[index] = field }
