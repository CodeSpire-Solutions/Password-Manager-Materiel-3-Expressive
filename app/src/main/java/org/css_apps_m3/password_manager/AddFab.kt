package org.css_apps_m3.password_manager

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController

@Composable
fun AddFab(navController: NavController) {
    // Collect the reactive custom accent color
    val accentColor by AppPrefs.customAccent.collectAsState()

    FloatingActionButton(
        onClick = { navController.navigate("add") }
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add Password")
    }
}