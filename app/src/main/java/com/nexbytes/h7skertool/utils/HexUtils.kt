package com.nexbytes.h7skertool.utils

object HexUtils {
    fun toHexDump(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        val sb = StringBuilder()
        val ascii = StringBuilder()
        for ((i, b) in bytes.withIndex()) {
            if (i > 0 && i % 16 == 0) { sb.append("  |").append(ascii).append("|\n"); ascii.clear() }
            if (i % 16 == 0) sb.append(String.format("%08X  ", i))
            sb.append(String.format("%02X ", b))
            val c = b.toInt().and(0xFF).toChar()
            ascii.append(if (c.code in 32..126) c else '.')
        }
        val rem = bytes.size % 16
        if (rem != 0) repeat(16 - rem) { sb.append("   ") }
        sb.append("  |").append(ascii).append("|")
        return sb.toString()
    }

    fun toSimpleHex(bytes: ByteArray?): String =
        bytes?.joinToString(" ") { String.format("%02X", it) } ?: ""
}
