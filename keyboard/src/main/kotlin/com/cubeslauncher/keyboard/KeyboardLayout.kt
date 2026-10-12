package com.cubeslauncher.keyboard

enum class KeyType {
    LETTER, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOL_SWITCH, ABC_SWITCH,
    MORE_SYMBOLS, COMMA, PERIOD, EMOJI
}

enum class ShiftState {
    OFF, SINGLE, CAPS_LOCK
}

data class Key(
    val label: String,
    val outputChar: Char? = null,
    val type: KeyType = KeyType.LETTER,
    val widthWeight: Float = 1f,
    val alternates: List<Char> = emptyList(),
    val iconName: String? = null
)

data class Row(val keys: List<Key>)

data class KeyboardPage(val rows: List<Row>, val isEmoji: Boolean = false)

object KeyboardLayouts {

    val QWERTY = KeyboardPage(
        rows = listOf(
            // QWERTY row (numbers as first long-press alternate, like Gboard)
            Row(listOf(
                Key("q", 'q', alternates = listOf('1')),
                Key("w", 'w', alternates = listOf('2')),
                Key("e", 'e', alternates = listOf('3', '\u00E8', '\u00E9', '\u00EA', '\u00EB', '\u0113')),
                Key("r", 'r', alternates = listOf('4')),
                Key("t", 't', alternates = listOf('5')),
                Key("y", 'y', alternates = listOf('6', '\u00FD', '\u00FF')),
                Key("u", 'u', alternates = listOf('7', '\u00F9', '\u00FA', '\u00FB', '\u00FC', '\u016B')),
                Key("i", 'i', alternates = listOf('8', '\u00EC', '\u00ED', '\u00EE', '\u00EF', '\u012B')),
                Key("o", 'o', alternates = listOf('9', '\u00F2', '\u00F3', '\u00F4', '\u00F5', '\u00F6', '\u00F8', '\u014D')),
                Key("p", 'p', alternates = listOf('0'))
            )),
            // ASDF row
            Row(listOf(
                Key("a", 'a', alternates = listOf('\u00E0', '\u00E1', '\u00E2', '\u00E3', '\u00E4', '\u00E5', '\u00E6')),
                Key("s", 's', alternates = listOf('\u00DF', '\u015B', '\u0161')),
                Key("d", 'd'), Key("f", 'f'), Key("g", 'g'),
                Key("h", 'h'), Key("j", 'j'), Key("k", 'k'),
                Key("l", 'l')
            )),
            // Shift row
            Row(listOf(
                Key("", type = KeyType.SHIFT, widthWeight = 1.5f, iconName = "shift"),
                Key("z", 'z', alternates = listOf('\u017E', '\u017A', '\u017C')),
                Key("x", 'x'),
                Key("c", 'c', alternates = listOf('\u00E7', '\u0107', '\u010D')),
                Key("v", 'v'), Key("b", 'b'),
                Key("n", 'n', alternates = listOf('\u00F1', '\u0144')),
                Key("m", 'm'),
                Key("", type = KeyType.BACKSPACE, widthWeight = 1.5f, iconName = "backspace")
            )),
            // Bottom row (matches Gboard: ?123 , emoji SPACE . enter)
            Row(listOf(
                Key("&123", type = KeyType.SYMBOL_SWITCH, widthWeight = 1.2f),
                Key(",", ',', type = KeyType.COMMA),
                Key("", type = KeyType.EMOJI, widthWeight = 1f, iconName = "emoji"),
                Key("", ' ', type = KeyType.SPACE, widthWeight = 3.6f),
                Key(".", '.', type = KeyType.PERIOD),
                Key("", type = KeyType.ENTER, widthWeight = 1.2f, iconName = "enter")
            ))
        )
    )

    val QWERTY_WITH_NUMBER_ROW = KeyboardPage(
        rows = listOf(
            // Dedicated number row
            Row(listOf(
                Key("1", '1'), Key("2", '2'), Key("3", '3'), Key("4", '4'), Key("5", '5'),
                Key("6", '6'), Key("7", '7'), Key("8", '8'), Key("9", '9'), Key("0", '0')
            )),
            // QWERTY row (same as QWERTY but without number alternates on q/w/r/t/p)
            Row(listOf(
                Key("q", 'q'),
                Key("w", 'w'),
                Key("e", 'e', alternates = listOf('\u00E8', '\u00E9', '\u00EA', '\u00EB', '\u0113')),
                Key("r", 'r'),
                Key("t", 't'),
                Key("y", 'y', alternates = listOf('\u00FD', '\u00FF')),
                Key("u", 'u', alternates = listOf('\u00F9', '\u00FA', '\u00FB', '\u00FC', '\u016B')),
                Key("i", 'i', alternates = listOf('\u00EC', '\u00ED', '\u00EE', '\u00EF', '\u012B')),
                Key("o", 'o', alternates = listOf('\u00F2', '\u00F3', '\u00F4', '\u00F5', '\u00F6', '\u00F8', '\u014D')),
                Key("p", 'p')
            )),
            // ASDF row
            Row(listOf(
                Key("a", 'a', alternates = listOf('\u00E0', '\u00E1', '\u00E2', '\u00E3', '\u00E4', '\u00E5', '\u00E6')),
                Key("s", 's', alternates = listOf('\u00DF', '\u015B', '\u0161')),
                Key("d", 'd'), Key("f", 'f'), Key("g", 'g'),
                Key("h", 'h'), Key("j", 'j'), Key("k", 'k'),
                Key("l", 'l')
            )),
            // Shift row
            Row(listOf(
                Key("", type = KeyType.SHIFT, widthWeight = 1.5f, iconName = "shift"),
                Key("z", 'z', alternates = listOf('\u017E', '\u017A', '\u017C')),
                Key("x", 'x'),
                Key("c", 'c', alternates = listOf('\u00E7', '\u0107', '\u010D')),
                Key("v", 'v'), Key("b", 'b'),
                Key("n", 'n', alternates = listOf('\u00F1', '\u0144')),
                Key("m", 'm'),
                Key("", type = KeyType.BACKSPACE, widthWeight = 1.5f, iconName = "backspace")
            )),
            // Bottom row
            Row(listOf(
                Key("&123", type = KeyType.SYMBOL_SWITCH, widthWeight = 1.2f),
                Key(",", ',', type = KeyType.COMMA),
                Key("", type = KeyType.EMOJI, widthWeight = 1f, iconName = "emoji"),
                Key("", ' ', type = KeyType.SPACE, widthWeight = 3.6f),
                Key(".", '.', type = KeyType.PERIOD),
                Key("", type = KeyType.ENTER, widthWeight = 1.2f, iconName = "enter")
            ))
        )
    )

    val SYMBOLS_1 = KeyboardPage(
        rows = listOf(
            Row(listOf(
                Key("1", '1'), Key("2", '2'), Key("3", '3'), Key("4", '4'), Key("5", '5'),
                Key("6", '6'), Key("7", '7'), Key("8", '8'), Key("9", '9'), Key("0", '0')
            )),
            Row(listOf(
                Key("@", '@'), Key("#", '#'), Key("$", '$'),
                Key("%", '%'), Key("&", '&'), Key("-", '-'),
                Key("+", '+'), Key("(", '('), Key(")", ')')
            )),
            Row(listOf(
                Key("more", type = KeyType.MORE_SYMBOLS, widthWeight = 1.5f),
                Key("*", '*'), Key("\"", '"'), Key("'", '\''),
                Key(":", ':'), Key(";", ';'), Key("!", '!'), Key("?", '?'),
                Key("", type = KeyType.BACKSPACE, widthWeight = 1.5f, iconName = "backspace")
            )),
            Row(listOf(
                Key("ABC", type = KeyType.ABC_SWITCH, widthWeight = 1.2f),
                Key(",", ',', type = KeyType.COMMA),
                Key("", type = KeyType.EMOJI, widthWeight = 1f, iconName = "emoji"),
                Key("", ' ', type = KeyType.SPACE, widthWeight = 3.6f),
                Key(".", '.', type = KeyType.PERIOD),
                Key("", type = KeyType.ENTER, widthWeight = 1.2f, iconName = "enter")
            ))
        )
    )

    val SYMBOLS_2 = KeyboardPage(
        rows = listOf(
            Row(listOf(
                Key("~", '~'), Key("`", '`'), Key("|", '|'), Key("\u00B7", '\u00B7'),
                Key("\u221A", '\u221A'), Key("\u03C0", '\u03C0'), Key("\u00F7", '\u00F7'),
                Key("\u00D7", '\u00D7'), Key("\u00B6", '\u00B6'), Key("\u0394", '\u0394')
            )),
            Row(listOf(
                Key("\u00A3", '\u00A3'), Key("\u00A5", '\u00A5'), Key("\u20AC", '\u20AC'),
                Key("\u00A2", '\u00A2'), Key("^", '^'), Key("\u00B0", '\u00B0'),
                Key("=", '='), Key("{", '{'), Key("}", '}')
            )),
            Row(listOf(
                Key("&123", type = KeyType.SYMBOL_SWITCH, widthWeight = 1.5f),
                Key("\\", '\\'), Key("\u00A9", '\u00A9'), Key("\u00AE", '\u00AE'),
                Key("\u2122", '\u2122'), Key("\u2105", '\u2105'),
                Key("[", '['), Key("]", ']'),
                Key("", type = KeyType.BACKSPACE, widthWeight = 1.5f, iconName = "backspace")
            )),
            Row(listOf(
                Key("ABC", type = KeyType.ABC_SWITCH, widthWeight = 1.2f),
                Key("<", '<', type = KeyType.COMMA),
                Key("", type = KeyType.EMOJI, widthWeight = 1f, iconName = "emoji"),
                Key("", ' ', type = KeyType.SPACE, widthWeight = 3.6f),
                Key(">", '>', type = KeyType.PERIOD),
                Key("", type = KeyType.ENTER, widthWeight = 1.2f, iconName = "enter")
            ))
        )
    )

    // Emoji page with common emojis
    val EMOJI_PAGE = KeyboardPage(
        rows = listOf(
            Row(listOf(
                Key("\uD83D\uDE00"), Key("\uD83D\uDE02"), Key("\uD83D\uDE0D"), Key("\uD83E\uDD70"),
                Key("\uD83D\uDE0E"), Key("\uD83D\uDE22"), Key("\uD83D\uDE2D"), Key("\uD83E\uDD23")
            )),
            Row(listOf(
                Key("\uD83D\uDE0A"), Key("\uD83D\uDE18"), Key("\uD83E\uDD7A"), Key("\uD83D\uDE4F"),
                Key("\uD83D\uDC4D"), Key("\uD83D\uDC4E"), Key("\uD83D\uDC4B"), Key("\uD83D\uDCAA")
            )),
            Row(listOf(
                Key("\u2764\uFE0F"), Key("\uD83D\uDD25"), Key("\u2728"), Key("\uD83C\uDF89"),
                Key("\uD83D\uDCAF"), Key("\uD83D\uDE4C"), Key("\uD83E\uDD1D"), Key("\uD83D\uDC4F")
            )),
            Row(listOf(
                Key("\uD83D\uDE01"), Key("\uD83D\uDE05"), Key("\uD83D\uDE09"), Key("\uD83D\uDE0B"),
                Key("\uD83E\uDD14"), Key("\uD83D\uDE44"), Key("\uD83D\uDE34"), Key("\uD83D\uDE31")
            )),
            Row(listOf(
                Key("\uD83D\uDC80"), Key("\uD83E\uDD21"), Key("\uD83D\uDC40"), Key("\uD83D\uDE08"),
                Key("\uD83E\uDD73"), Key("\uD83E\uDD29"), Key("\uD83D\uDE07"), Key("\uD83D\uDE0F")
            )),
            // Bottom nav row
            Row(listOf(
                Key("ABC", type = KeyType.ABC_SWITCH, widthWeight = 1.5f),
                Key("", type = KeyType.BACKSPACE, widthWeight = 1.5f, iconName = "backspace"),
                Key("", ' ', type = KeyType.SPACE, widthWeight = 4f),
                Key("", type = KeyType.ENTER, widthWeight = 1.5f, iconName = "enter")
            ))
        ),
        isEmoji = true
    )
}
