package app.kanapon

import app.kanapon.data.StrokeData
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** Bundled assets and golden fixtures, read straight from the source tree. */
object TestData {
    private fun asset(path: String): String {
        // Unit tests run with the module directory as the working directory.
        val f = listOf(File("src/main/assets/$path"), File("app/src/main/assets/$path")).first { it.exists() }
        return f.readText()
    }

    val strokeData: StrokeData by lazy { StrokeData.parse(asset("data/strokes.json"), asset("data/klee-strokes.json")) }

    fun fixture(name: String): JsonArray {
        val text = TestData::class.java.getResource("/fixtures/$name")!!.readText()
        return Json.parseToJsonElement(text) as JsonArray
    }

    fun assetText(path: String): String = asset(path)
}

internal fun JsonElement?.isNullJson() = this == null || this.toString() == "null"
