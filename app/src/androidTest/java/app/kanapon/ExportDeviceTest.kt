package app.kanapon

import android.content.ContentUris
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kanapon.data.Gojuon
import app.kanapon.data.KanaSet
import app.kanapon.export.AndroidExporter
import app.kanapon.export.Paper
import app.kanapon.export.WorksheetLayout
import app.kanapon.export.WorksheetSpec
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What Robolectric cannot do: real PdfDocument output, and files landing in MediaStore. */
@RunWith(AndroidJUnit4::class)
class ExportDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun worksheetsHaveTheWebAppsPageCounts() {
        listOf(
            Triple(KanaSet.Hiragana, Paper.A4, 8) to 5,
            Triple(KanaSet.Katakana, Paper.Letter, 8) to 6,
            Triple(KanaSet.Hiragana, Paper.A4, 12) to 4,
            Triple(KanaSet.Hiragana, Paper.A4, 4) to 10
        ).forEach { (c, pages) ->
            val (set, paper, per) = c
            val spec = WorksheetSpec(Gojuon.listFor(set), per, 0.3, true, paper, set.id)
            val doc = AndroidExporter.writeWorksheet(spec, WorksheetLayout.of(paper, per, spec.chars.size), Typeface.SERIF)
            assertEquals(pages, doc.pages.size)
            val out = ByteArrayOutputStream()
            doc.writeTo(out)
            doc.close()
            assertEquals("%PDF", out.toByteArray().copyOfRange(0, 4).decodeToString())
        }
    }

    private fun findAndDelete(collection: Uri, name: String): Long {
        val resolver = context.contentResolver
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(name),
            null
        )!!.use { c ->
            assertTrue("$name is in MediaStore", c.moveToFirst())
            val size = c.getLong(1)
            resolver.delete(ContentUris.withAppendedId(collection, c.getLong(0)), null, null)
            return size
        }
    }

    @Test
    fun savedFilesLandInDownloadsAndPictures() = runTest {
        val exporter = AndroidExporter(context)
        val ka = Gojuon.find("か")!!
        val downloads = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        assertEquals("kanapon-hiragana-ka.svg", exporter.saveGlyphSvg(ka, KanaSet.Hiragana))
        assertTrue(findAndDelete(downloads, "kanapon-hiragana-ka.svg") > 1000)

        assertEquals("kanapon-hiragana-ka.jpg", exporter.saveGlyphJpeg(ka, KanaSet.Hiragana))
        assertTrue(findAndDelete(images, "kanapon-hiragana-ka.jpg") > 10_000)

        val spec = WorksheetSpec(Gojuon.listFor(KanaSet.Hiragana), 8, 0.3, true, Paper.A4, "hiragana")
        assertEquals(5, exporter.saveWorksheet(spec))
        assertTrue(findAndDelete(downloads, "kanapon-hiragana-a4.pdf") > 10_000)
    }
}
