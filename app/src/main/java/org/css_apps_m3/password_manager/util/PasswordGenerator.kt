package org.css_apps_m3.password_manager.util

import java.security.SecureRandom

object PasswordGenerator {
    private const val UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWERCASE = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#%+-_?"
    private val secureRandom = SecureRandom()

    fun generate(length: Int = 20): String {
        require(length >= 4) { "Password length must be at least 4" }

        val requiredCharacters = listOf(
            UPPERCASE.randomCharacter(),
            LOWERCASE.randomCharacter(),
            DIGITS.randomCharacter(),
            SYMBOLS.randomCharacter()
        ).toMutableList()
        val allCharacters = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS

        repeat(length - requiredCharacters.size) {
            requiredCharacters += allCharacters.randomCharacter()
        }
        for (index in requiredCharacters.lastIndex downTo 1) {
            val swapIndex = secureRandom.nextInt(index + 1)
            val current = requiredCharacters[index]
            requiredCharacters[index] = requiredCharacters[swapIndex]
            requiredCharacters[swapIndex] = current
        }
        return requiredCharacters.joinToString("")
    }

    private fun String.randomCharacter(): Char = this[secureRandom.nextInt(length)]
}
