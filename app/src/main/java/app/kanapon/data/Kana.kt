package app.kanapon.data

/** One gojūon character: the glyph, its Hepburn romaji and its KanjiVG stroke count. */
data class Kana(val char: String, val romaji: String, val strokes: Int)

enum class KanaSet(val id: String, val label: String) {
    Hiragana("hiragana", "Hiragana"),
    Katakana("katakana", "Katakana")
    ;

    val other: KanaSet get() = if (this == Hiragana) Katakana else Hiragana

    /** The set's own name, in its own script, for the partner label on the chart detail. */
    val nativeName: String get() = if (this == Hiragana) "ひらがな" else "カタカナ"
}

/** Position in the 11 x 5 table. Both tables put their gaps in the same places. */
data class GridPos(val row: Int, val col: Int)

object Gojuon {
    /** Row of the syllabic n, which belongs to no 行. */
    const val N_ROW = 10

    fun rowsFor(set: KanaSet): List<List<Kana?>> = if (set == KanaSet.Katakana) KATAKANA_ROWS else HIRAGANA_ROWS

    private val lists = KanaSet.entries.associateWith { set -> rowsFor(set).flatten().filterNotNull() }

    fun listFor(set: KanaSet): List<Kana> = lists.getValue(set)

    /** Hiragana then katakana: the order every ranking breaks ties in. */
    val ALL: List<Kana> = listFor(KanaSet.Hiragana) + listFor(KanaSet.Katakana)

    val TOTAL: Int = ALL.size

    private val byChar = ALL.associateBy { it.char }

    fun find(char: String): Kana? = byChar[char]

    /** Which syllabary a character belongs to, or null if it is not one of the 92. */
    fun setOf(char: String): KanaSet? = when {
        listFor(KanaSet.Hiragana).any { it.char == char } -> KanaSet.Hiragana
        listFor(KanaSet.Katakana).any { it.char == char } -> KanaSet.Katakana
        else -> null
    }

    fun indexIn(set: KanaSet, char: String): Int = listFor(set).indexOfFirst { it.char == char }

    fun posOf(set: KanaSet, char: String): GridPos? {
        rowsFor(set).forEachIndexed { r, row ->
            row.forEachIndexed { c, k -> if (k?.char == char) return GridPos(r, c) }
        }
        return null
    }

    fun at(set: KanaSet, pos: GridPos): Kana? = rowsFor(set).getOrNull(pos.row)?.getOrNull(pos.col)
}
