package com.tqmane.filmsim.util

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

data class CubeLUT(val size: Int, val data: FloatBuffer)

object CubeLUTParser {
    fun parse(context: Context, assetPath: String): CubeLUT? {
        try {
            val inputStream = context.assets.open(assetPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            var size = -1
            val dataList = mutableListOf<Float>()
            
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("TITLE") || trimmed.startsWith("DOMAIN_")) {
                    line = reader.readLine()
                    continue
                }
                
                if (trimmed.startsWith("LUT_3D_SIZE")) {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        size = parts[1].toInt()
                    }
                } else {
                    // Assuming data line: R G B
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        try {
                            dataList.add(parts[0].toFloat())
                            dataList.add(parts[1].toFloat())
                            dataList.add(parts[2].toFloat())
                        } catch (e: NumberFormatException) {
                            // Ignore malformed lines
                        }
                    }
                }
                line = reader.readLine()
            }
            reader.close()

            if (size == -1 || dataList.isEmpty()) return null

            val floatBuffer = ByteBuffer.allocateDirect(dataList.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            
            for (f in dataList) {
                floatBuffer.put(f)
            }
            floatBuffer.position(0)
            
            return CubeLUT(size, floatBuffer)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}