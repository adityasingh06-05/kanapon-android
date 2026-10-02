package app.kanapon.ui

import app.kanapon.data.CharStat
import app.kanapon.data.Gojuon
import app.kanapon.data.Stats
import app.kanapon.data.StatsMath
import java.time.LocalDate

/** The prototype's sample history (Kanapon App.dc.html, seed()), for screenshots of a lived-in app. */
fun seedHistory(today: LocalDate): Stats {
    var s = 11L
    fun r(): Double {
        s = (s * 16807) % 2147483647
        return s / 2147483647.0
    }
    val chars = LinkedHashMap<String, CharStat>()
    val days = LinkedHashMap<String, Int>()
    var strokes = 0
    Gojuon.ALL.take(66).forEach { k ->
        if (r() < 0.6) {
            val a = 1 + Math.floor(r() * 9).toInt()
            chars[k.char] = CharStat(a, 24 + Math.floor(r() * 70).toInt())
            strokes += a * k.strokes
        }
    }
    for (i in 1 until 80) {
        if (i < 6 || r() < 0.5) days[StatsMath.dateKey(today.minusDays(i.toLong()))] = 1 + Math.floor(r() * 24).toInt()
    }
    return Stats(chars, days, strokes)
}
