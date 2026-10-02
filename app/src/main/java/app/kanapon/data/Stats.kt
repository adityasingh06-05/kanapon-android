package app.kanapon.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/*
 * Practice history, ported from the web app's src/lib/stats.js. The shape matches its
 * kana-practice-v1 record: per character, attempts and the best counted shape score; per local
 * calendar day, the number of attempts; and the running total of strokes written.
 */

@Serializable
data class CharStat(val attempts: Int, val best: Int)

@Serializable
data class Stats(val chars: Map<String, CharStat> = emptyMap(), val days: Map<String, Int> = emptyMap(), val strokes: Int = 0) {
    val attempted: Int get() = chars.size
    val isEmpty: Boolean get() = chars.isEmpty() && strokes == 0
}

/** One square of the practice heatmap. */
data class HeatDay(val key: String, val count: Int)

object StatsMath {
    private val json = Json { ignoreUnknownKeys = true }

    /** Local calendar date as YYYY-MM-DD. */
    fun dateKey(d: LocalDate): String = d.toString()

    /** Parses stored JSON, keeping only what makes sense: unreadable input is an empty history. */
    fun parse(text: String?): Stats {
        if (text.isNullOrBlank()) return Stats()
        return try {
            normalise(json.parseToJsonElement(text))
        } catch (_: Exception) {
            Stats()
        }
    }

    fun encode(stats: Stats): String = json.encodeToString(Stats.serializer(), stats)

    private fun num(e: JsonElement?): Double {
        val v = (e as? JsonPrimitive)?.doubleOrNull ?: return 0.0
        return if (v.isFinite()) v else 0.0
    }

    fun normalise(raw: JsonElement?): Stats {
        val obj = raw as? JsonObject ?: return Stats()
        val chars = LinkedHashMap<String, CharStat>()
        (obj["chars"] as? JsonObject)?.forEach { (char, v) ->
            val o = v as? JsonObject ?: return@forEach
            val attempts = num(o["attempts"]).toInt()
            val best = num(o["best"]).toInt()
            if (attempts > 0) chars[char] = CharStat(attempts, best)
        }
        val days = LinkedHashMap<String, Int>()
        (obj["days"] as? JsonObject)?.forEach { (day, v) ->
            val n = num(v).toInt()
            if (n > 0) days[day] = n
        }
        return Stats(chars, days, num(obj["strokes"]).toInt())
    }

    /**
     * One finished attempt at one character. [strokeCount] is the strokes drawn in this attempt;
     * [countsForBest] is false for wrong order, direction or count, which is still practice but can
     * never raise the best. Returns the new history and the previous best.
     */
    fun recordAttempt(
        stats: Stats,
        char: String,
        score: Int,
        strokeCount: Int,
        countsForBest: Boolean,
        today: LocalDate
    ): Pair<Stats, Int> {
        val c = stats.chars[char] ?: CharStat(0, 0)
        val prevBest = c.best
        val next = CharStat(c.attempts + 1, if (countsForBest) maxOf(c.best, score) else c.best)
        val key = dateKey(today)
        return Stats(
            chars = stats.chars + (char to next),
            days = stats.days + (key to (stats.days[key] ?: 0) + 1),
            strokes = stats.strokes + strokeCount
        ) to prevBest
    }

    /** Consecutive days up to and including today. A day with no practice yet does not break a streak alive from yesterday. */
    fun streak(days: Map<String, Int>, today: LocalDate): Int {
        var n = 0
        var d = today
        val limit = days.size + 1
        for (i in 0 until limit) {
            if ((days[dateKey(d)] ?: 0) > 0) {
                n++
            } else if (i > 0) {
                break
            }
            d = d.minusDays(1)
        }
        return n
    }

    /** Columns of 7 days ending today, oldest column first. Always the full grid. */
    fun heatmap(days: Map<String, Int>, today: LocalDate, weeks: Int = 26): List<List<HeatDay>> {
        val start = today.minusDays((weeks * 7 - 1).toLong())
        val cols = ArrayList<List<HeatDay>>()
        for (w in 0 until weeks) {
            val col = ArrayList<HeatDay>()
            for (d in 0 until 7) {
                val dt = start.plusDays((w * 7 + d).toLong())
                if (dt.isAfter(today)) break
                val key = dateKey(dt)
                col.add(HeatDay(key, days[key] ?: 0))
            }
            if (col.isNotEmpty()) cols.add(col)
        }
        return cols
    }

    /** Heat level 0..4 for a day, against the busiest day on record (not just the visible window). */
    fun bucket(count: Int, max: Int): Int = when {
        count <= 0 -> 0
        max <= 1 -> 4
        else -> ceil(4.0 * count / max).toInt().coerceIn(1, 4)
    }

    /** Weakest first: lowest best, then most attempts. Only characters actually attempted. */
    fun weakest(stats: Stats, limit: Int = Int.MAX_VALUE): List<Kana> = Gojuon.ALL
        .filter { stats.chars.containsKey(it.char) }
        .sortedWith { a, b ->
            val ca = stats.chars.getValue(a.char)
            val cb = stats.chars.getValue(b.char)
            if (ca.best != cb.best) ca.best - cb.best else cb.attempts - ca.attempts
        }
        .take(limit)
}
