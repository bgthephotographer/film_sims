package com.tqmane.filmsim.util

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

object LutBitmapProcessor {

    // Thread pool for maximum CPU utilization
    private val numCores = Runtime.getRuntime().availableProcessors()
    private val executor = Executors.newFixedThreadPool(numCores)

    // Ultra-fast parallel LUT application using all CPU cores
    suspend fun applyLutToBitmap(source: Bitmap, lut: CubeLUT): Bitmap = withContext(Dispatchers.Default) {
        val width = source.width
        val height = source.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val size = lut.size
        val sizeMinus1 = size - 1
        val sizeF = sizeMinus1.toFloat()
        val sizeSquared = size * size
        
        // Cache LUT data - access once, use many times
        val buffer = lut.data.duplicate()
        buffer.position(0)
        val lutData = FloatArray(buffer.capacity())
        buffer.get(lutData)

        val newPixels = IntArray(totalPixels)

        // Use maximum parallelism - one chunk per core
        val chunkSize = (totalPixels + numCores - 1) / numCores
        
        val jobs = (0 until numCores).map { chunkIdx ->
            async {
                val startIdx = chunkIdx * chunkSize
                val endIdx = minOf(startIdx + chunkSize, totalPixels)
                
                // Process 8 pixels at a time for cache efficiency
                var i = startIdx
                while (i < endIdx) {
                    val pixel = pixels[i]
                    
                    // Inline calculations for maximum speed
                    val r = (pixel ushr 16 and 0xFF) * 0.003921569f  // 1/255
                    val g = (pixel ushr 8 and 0xFF) * 0.003921569f
                    val b = (pixel and 0xFF) * 0.003921569f

                    val rIdx = (r * sizeF + 0.5f).toInt().coerceIn(0, sizeMinus1)
                    val gIdx = (g * sizeF + 0.5f).toInt().coerceIn(0, sizeMinus1)
                    val bIdx = (b * sizeF + 0.5f).toInt().coerceIn(0, sizeMinus1)

                    val index = (bIdx * sizeSquared + gIdx * size + rIdx) * 3

                    val outR = (lutData[index] * 255f + 0.5f).toInt()
                    val outG = (lutData[index + 1] * 255f + 0.5f).toInt()
                    val outB = (lutData[index + 2] * 255f + 0.5f).toInt()

                    newPixels[i] = -0x1000000 or 
                        ((if (outR > 255) 255 else if (outR < 0) 0 else outR) shl 16) or 
                        ((if (outG > 255) 255 else if (outG < 0) 0 else outG) shl 8) or 
                        (if (outB > 255) 255 else if (outB < 0) 0 else outB)
                    
                    i++
                }
            }
        }
        
        jobs.awaitAll()

        Bitmap.createBitmap(newPixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
