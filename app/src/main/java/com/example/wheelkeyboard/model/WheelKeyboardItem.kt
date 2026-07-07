package com.example.wheelkeyboard.model

sealed class WheelKeyboardItem(val label: String) {
    data class Letter(val value: Char) : WheelKeyboardItem(value.toString())
    data class Digit(val value: Char) : WheelKeyboardItem(value.toString())
    data class Punctuation(val value: Char) : WheelKeyboardItem(value.toString())
    data object Space : WheelKeyboardItem("Space")
    data object Delete : WheelKeyboardItem("Delete")
    data object Search : WheelKeyboardItem("Search")
    data object VoiceSearch : WheelKeyboardItem("Voice")
    data object Done : WheelKeyboardItem("Done")
    data object ClearText : WheelKeyboardItem("Clear")
    data object ToggleSymbols : WheelKeyboardItem("&123")
    data object ToggleLetters : WheelKeyboardItem("ABC")

    companion object {
        private val optionItems: List<WheelKeyboardItem> =
            listOf(Space, Delete, Search, VoiceSearch, Done, ClearText)
        private val punctuationCharacters: List<Char> = "&#()!?:.-_\"/$%+[]".toList()

        val letterOrder: List<WheelKeyboardItem> =
            ('A'..'Z').map(::Letter) + listOf(ToggleSymbols) + optionItems

        val symbolOrder: List<WheelKeyboardItem> =
            ('0'..'9').map(::Digit) + punctuationCharacters.map(::Punctuation) +
                listOf(ToggleLetters) + optionItems

        val fixedOrder: List<WheelKeyboardItem> = letterOrder

        fun indexOfCharacter(char: Char, items: List<WheelKeyboardItem> = fixedOrder): Int? {
            val normalized = char.uppercaseChar()
            return items.indexOfFirst {
                when (it) {
                    is Letter -> it.value == normalized
                    is Digit -> it.value == normalized
                    is Punctuation -> it.value == char
                    Space -> char.isWhitespace()
                    else -> false
                }
            }.takeIf { it >= 0 }
        }
    }
}
