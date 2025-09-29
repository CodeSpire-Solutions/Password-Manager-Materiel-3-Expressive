package org.css_apps_m3.password_manager

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AccentColorPicker(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    // Example palette inspired by Material 3 Expressive colors
    val colors = listOf(
        0xFFB00020, 0xFF3700B3, 0xFF03DAC5, 0xFF018786,
        0xFFFF9800, 0xFFFFC107, 0xFF4CAF50, 0xFF2196F3,
        0xFFE91E63, 0xFF9C27B0, 0xFF795548, 0xFF607D8B
    )

    // Two rows of color swatches
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (row in colors.chunked(4)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                for (colorInt in row) {
                    val color = Color(colorInt)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelected(colorInt.toInt()) }
                            .border(
                                width = if (selectedColor.toLong() == colorInt) 3.dp else 0.dp,
                                color = if (selectedColor.toLong() == colorInt) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
