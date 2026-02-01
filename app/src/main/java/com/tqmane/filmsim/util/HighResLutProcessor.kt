package com.tqmane.filmsim.util

import android.graphics.Bitmap
import com.tqmane.filmsim.util.CubeLUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/**
 * High-quality LUT processor for full-resolution image export.
 * Uses trilinear interpolation for better quality.
 * Memory-efficient strip-based processing to avoid OOM on large images.
 */
object HighResLutProcessor {

    // Strip height for memory-efficient processing
    // 512 rows provides good balance between JNI overhead and memory usage
    private const val STRIP_HEIGHT = 512

    /**
     * Apply LUT to full-resolution bitmap with intensity blending.
     * Uses strip-based processing for memory efficiency and parallel processing for speed.
     */
    suspend fun applyLut(
        source: Bitmap,
        lut: CubeLUT,
        intensity: Float
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        
        // Create output bitmap (ARGB_8888 for best quality)
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Cache LUT data for fast access
        val buffer = lut.data.duplicate()
        buffer.position(0)
        val lutData = FloatArray(buffer.capacity())
        buffer.get(lutData)
        
        val lutSize = lut.size
        val lutSizeF = (lutSize - 1).toFloat()
        val lutSizeSquared = lutSize * lutSize
        
        // Process image in strips to minimize memory usage
        var y = 0
        while (y < height) {
            val stripHeight = minOf(STRIP_HEIGHT, height - y)
            val stripPixels = width * stripHeight
            
            // Read strip from source
            val pixels = IntArray(stripPixels)
            source.getPixels(pixels, 0, width, 0, y, width, stripHeight)
            
            // Process strip (output in-place to save memory)
            val newPixels = IntArray(stripPixels)
            
            // Parallel processing within strip
            val numChunks = if (stripPixels > 100_000) 8 else if (stripPixels > 10_000) 4 else 1
            val chunkSize = (stripPixels + numChunks - 1) / numChunks
            
            val jobs = (0 until numChunks).map { chunkIndex ->
                async {
                    val start = chunkIndex * chunkSize
                    val end = minOf(start + chunkSize, stripPixels)
                    
                    for (i in start until end) {
                        val pixel = pixels[i]
                        
                        // Extract RGB (0-255) and convert to 0-1
                        val origR = (pixel ushr 16 and 0xFF)
                        val origG = (pixel ushr 8 and 0xFF)
                        val origB = (pixel and 0xFF)
                        val origA = (pixel ushr 24 and 0xFF)
                        
                        val r = origR / 255f
                        val g = origG / 255f
                        val b = origB / 255f
                        
                        // Trilinear interpolation for high quality
                        val rPos = r * lutSizeF
                        val gPos = g * lutSizeF
                        val bPos = b * lutSizeF
                        
                        val r0 = rPos.toInt().coerceIn(0, lutSize - 2)
                        val g0 = gPos.toInt().coerceIn(0, lutSize - 2)
                        val b0 = bPos.toInt().coerceIn(0, lutSize - 2)
                        
                        val r1 = r0 + 1
                        val g1 = g0 + 1
                        val b1 = b0 + 1
                        
                        val rFrac = rPos - r0
                        val gFrac = gPos - g0
                        val bFrac = bPos - b0
                        
                        // Sample 8 corners of the LUT cube
                        fun sample(ri: Int, gi: Int, bi: Int, channel: Int): Float {
                            val idx = (bi * lutSizeSquared + gi * lutSize + ri) * 3 + channel
                            return lutData[idx]
                        }
                        
                        // Trilinear interpolation for each channel
                        fun interpolate(channel: Int): Float {
                            val c000 = sample(r0, g0, b0, channel)
                            val c100 = sample(r1, g0, b0, channel)
                            val c010 = sample(r0, g1, b0, channel)
                            val c110 = sample(r1, g1, b0, channel)
                            val c001 = sample(r0, g0, b1, channel)
                            val c101 = sample(r1, g0, b1, channel)
                            val c011 = sample(r0, g1, b1, channel)
                            val c111 = sample(r1, g1, b1, channel)
                            
                            val c00 = c000 + (c100 - c000) * rFrac
                            val c01 = c001 + (c101 - c001) * rFrac
                            val c10 = c010 + (c110 - c010) * rFrac
                            val c11 = c011 + (c111 - c011) * rFrac
                            
                            val c0 = c00 + (c10 - c00) * gFrac
                            val c1 = c01 + (c11 - c01) * gFrac
                            
                            return c0 + (c1 - c0) * bFrac
                        }
                        
                        val lutR = interpolate(0)
                        val lutG = interpolate(1)
                        val lutB = interpolate(2)
                        
                        // Blend with original based on intensity
                        val finalR: Int
                        val finalG: Int
                        val finalB: Int
                        
                        if (intensity >= 1f) {
                            finalR = (lutR * 255f + 0.5f).toInt().coerceIn(0, 255)
                            finalG = (lutG * 255f + 0.5f).toInt().coerceIn(0, 255)
                            finalB = (lutB * 255f + 0.5f).toInt().coerceIn(0, 255)
                        } else if (intensity <= 0f) {
                            finalR = origR
                            finalG = origG
                            finalB = origB
                        } else {
                            finalR = (origR + (lutR * 255f - origR) * intensity + 0.5f).toInt().coerceIn(0, 255)
                            finalG = (origG + (lutG * 255f - origG) * intensity + 0.5f).toInt().coerceIn(0, 255)
                            finalB = (origB + (lutB * 255f - origB) * intensity + 0.5f).toInt().coerceIn(0, 255)
                        }
                        
                        newPixels[i] = (origA shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
                    }
                }
            }
            
            jobs.awaitAll()
            
            // Write processed strip to output bitmap
            result.setPixels(newPixels, 0, width, 0, y, width, stripHeight)
            
            // Clear references to help GC
            // (pixels and newPixels go out of scope after this iteration)
            
            y += stripHeight
        }
        
        result
    }
}
