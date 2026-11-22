package com.tw1st.mahreader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tw1st.mahreader.data.ReadingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

class PdfEngine(private val context: Context) {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var tempFile: File? = null

    var pageCount: Int = 0
        private set

    // Memory cache for processed page bitmaps (key: "pageIndex_themeName")
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(30) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.byteCount / 1024)
        }
    }

    suspend fun openUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        close()

        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")

        // Copy to temporary cache file so PdfRenderer has a random-access file descriptor
        val temp = File(context.cacheDir, "current_read_${System.currentTimeMillis()}.pdf")
        tempFile = temp

        FileOutputStream(temp).use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()

        pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd!!)
        pageCount = renderer!!.pageCount
        pageCount
    }

    suspend fun renderPage(
        pageIndex: Int,
        targetWidth: Int = 1080,
        theme: ReadingTheme = ReadingTheme.AMOLED_DARK
    ): Bitmap = withContext(Dispatchers.Default) {
        val cacheKey = "${pageIndex}_${theme.name}_$targetWidth"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        val r = renderer ?: throw IllegalStateException("PdfRenderer not initialized")
        val page = synchronized(r) {
            r.openPage(pageIndex)
        }

        val pageWidth = page.width
        val pageHeight = page.height

        val scale = targetWidth.toFloat() / pageWidth.toFloat()
        val scaledWidth = targetWidth
        val scaledHeight = (pageHeight * scale).toInt()

        val rawBitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)

        synchronized(r) {
            page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
        }

        val finalBitmap = if (theme == ReadingTheme.ORIGINAL_LIGHT) {
            rawBitmap
        } else {
            applySmartDarkFilter(rawBitmap, theme)
        }

        memoryCache.put(cacheKey, finalBitmap)
        finalBitmap
    }

    /**
     * Smart Selective Pixel Filter:
     * - Identifies text and background pixels (grayscale) and flips them according to the selected theme.
     * - Detects chromatic/color pixels (illustrations, covers, character art, logos) and leaves them 100% UNTOUCHED!
     */
    private fun applySmartDarkFilter(source: Bitmap, theme: ReadingTheme): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val totalPixels = pixels.size
        val outPixels = IntArray(totalPixels)

        val isAmber = theme == ReadingTheme.EYE_STRAIN_AMBER
        val isCharcoal = theme == ReadingTheme.SOFT_CHARCOAL

        for (i in 0 until totalPixels) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Measure chromatic difference (saturation)
            val diffRG = abs(r - g)
            val diffGB = abs(g - b)
            val diffRB = abs(r - b)
            val maxDiff = max(max(diffRG, diffGB), diffRB)

            // If pixel contains color (illustrations, character hair, color spreads), keep it 100% original!
            if (maxDiff > 16) {
                outPixels[i] = pixel
            } else {
                // Grayscale element (text or background)
                val luminance = (r * 299 + g * 587 + b * 114) / 1000
                val inverted = 255 - luminance

                if (isAmber) {
                    // Eye Strain Amber: Deep warm midnight background (13, 10, 7) + Soothing warm amber text (232, 197, 140)
                    val factor = inverted / 255f
                    val outR = (13 + factor * (232 - 13)).toInt().coerceIn(0, 255)
                    val outG = (10 + factor * (197 - 10)).toInt().coerceIn(0, 255)
                    val outB = (7 + factor * (140 - 7)).toInt().coerceIn(0, 255)
                    outPixels[i] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
                } else if (isCharcoal) {
                    // Soft Charcoal: Dark charcoal background (22, 22, 25) + soft white text (214, 214, 220)
                    val factor = inverted / 255f
                    val outR = (22 + factor * (214 - 22)).toInt().coerceIn(0, 255)
                    val outG = (22 + factor * (214 - 22)).toInt().coerceIn(0, 255)
                    val outB = (25 + factor * (220 - 25)).toInt().coerceIn(0, 255)
                    outPixels[i] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
                } else {
                    // AMOLED Dark: Pure pitch black background (0, 0, 0) + Crisp white text (255, 255, 255)
                    outPixels[i] = (0xFF shl 24) or (inverted shl 16) or (inverted shl 8) or inverted
                }
            }
        }

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    fun clearCache() {
        memoryCache.evictAll()
    }

    fun close() {
        try {
            clearCache()
            renderer?.close()
            renderer = null
            pfd?.close()
            pfd = null
            tempFile?.delete()
            tempFile = null
            pageCount = 0
        } catch (_: Exception) {}
    }
}
