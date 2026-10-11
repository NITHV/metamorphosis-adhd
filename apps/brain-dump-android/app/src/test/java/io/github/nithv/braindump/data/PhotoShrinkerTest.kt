package io.github.nithv.braindump.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real image decoding/encoding on the JVM
class PhotoShrinkerTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun sizesMatchTheWebsite() {
        assertEquals(1600 to 1200, PhotoShrinker.scaledSize(4000, 3000))
        assertEquals(1200 to 1600, PhotoShrinker.scaledSize(3000, 4000))
        assertEquals(800 to 600, PhotoShrinker.scaledSize(800, 600)) // never enlarged
    }

    @Test
    fun decodeSizeSavesMemoryWithoutGoingBelowTheTarget() {
        assertEquals(2, PhotoShrinker.sampleSize(4000, 3000)) // decodes 2000 px, then scales to 1600
        assertEquals(4, PhotoShrinker.sampleSize(8160, 6120)) // 50 MP: 2040 px instead of 8160
        assertEquals(1, PhotoShrinker.sampleSize(1600, 1200))
    }

    @Test
    fun sidewaysPhotosAreTurnedUpright() {
        assertEquals(90f, PhotoShrinker.rotationDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(0f, PhotoShrinker.rotationDegrees(ExifInterface.ORIENTATION_NORMAL))
    }

    /** The whole job on a real 12 MP JPEG with a planted GPS location. */
    @Test
    fun shrinksRotatesAndDropsTheLocation() {
        val original = tmp.newFile("camera.jpg")
        val bitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(242, 154, 31)) }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        ExifInterface(original.path).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "12/1,58/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        assertTrue("test photo should carry GPS", ExifInterface(original.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null)

        val out = tmp.newFile("out.webp")
        PhotoShrinker.shrink({ original.inputStream() }, out)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(out.path, bounds)
        assertEquals("rotated upright and shrunk", 1200 to 1600, bounds.outWidth to bounds.outHeight)
        assertTrue("much smaller: ${out.length()} vs ${original.length()}", out.length() < original.length() / 4)
        val bytes = out.readBytes()
        assertFalse("no EXIF block, so no GPS", String(bytes, Charsets.ISO_8859_1).contains("Exif"))
    }

    @Test(expected = java.io.IOException::class)
    fun aNonImageIsRefused() {
        val notAPhoto = tmp.newFile("notes.txt").apply { writeText("hello") }
        PhotoShrinker.shrink({ notAPhoto.inputStream() }, tmp.newFile("out.webp"))
    }
}
