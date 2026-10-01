package app.kanapon.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double

/** One kana's KanjiVG stroke order: a centreline path per stroke and where each number goes. */
class StrokeOrder(val paths: List<String>, val numbers: List<Pair<Float, Float>>)

/**
 * The bundled stroke data: KanjiVG stroke order for the animation, and the Klee One centrelines
 * the matcher scores against. Both are CC BY-SA 3.0 derivatives of KanjiVG.
 */
class StrokeData(val order: Map<String, StrokeOrder>, val reference: Map<String, List<String>>) {
    companion object {
        fun parse(strokesJson: String, kleeJson: String): StrokeData {
            val order = (Json.parseToJsonElement(strokesJson) as JsonObject)["strokes"] as JsonObject
            val klee = (Json.parseToJsonElement(kleeJson) as JsonObject)["strokes"] as JsonObject
            return StrokeData(
                order.mapValues { (_, v) ->
                    val o = v as JsonObject
                    StrokeOrder(
                        (o["d"] as JsonArray).map { (it as JsonPrimitive).content },
                        (o["n"] as JsonArray).map { p ->
                            val a = p as JsonArray
                            (a[0] as JsonPrimitive).double.toFloat() to (a[1] as JsonPrimitive).double.toFloat()
                        }
                    )
                },
                klee.mapValues { (_, v) -> (v as JsonArray).map { (it as JsonPrimitive).content } }
            )
        }
    }
}
