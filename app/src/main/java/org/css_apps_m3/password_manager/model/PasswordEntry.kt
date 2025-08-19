package org.css_apps_m3.password_manager.model

import java.io.Serializable

data class PasswordEntry(
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val note: String? = null   // nullable + Defaultvalue
) : Serializable
