package com.example.wheelkeyboard.model

sealed class WheelKeyboardItem(val label: String) {
    data class Letter(val value: Char) : WheelKeyboardItem(value.toString())
    data class Digit(val value: Char) : WheelKeyboardItem(value.toString())
    data object Space : WheelKeyboardItem("Space")
    data object Delete : WheelKeyboardItem("Delete")
    data object Search : WheelKeyboardItem("Search")
    data object VoiceSearch : WheelKeyboardItem("Voice")
    data object Done : WheelKeyboardItem("Done")
    data object ClearText : WheelKeyboardItem("Clear")

    companion object {
        val fixedOrder: List<WheelKeyboardItem> =
            ('A'..'Z').map(::Letter) + ('0'..'9').map(::Digit) +
                listOf(Space, Delete, Search, VoiceSearch, Done, ClearText)

        fun indexOfCharacter(char: Char): Int? {
            val normalized = char.uppercaseChar()
            return fixedOrder.indexOfFirst {
                when (it) {
                    is Letter -> it.value == normalized
                    is Digit -> it.value == normalized
                    Space -> char.isWhitespace()
                    else -> false
                }
            }.takeIf { it >= 0 }
        }
    }
}
