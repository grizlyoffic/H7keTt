package com.nexbytes.h7skertool.utils

object GzipUtils {
    fun decompress(data: ByteArray?): ByteArray? {
        if (data == null || data.isEmpty()) return null
        return try {
            java.util.zip.GZIPInputStream(data.inputStream()).use { it.readBytes() }
        } catch (e: Exception) { 
            null 
        }
    }
}
