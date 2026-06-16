package com.nexbytes.h7skertool.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ExportUtils {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun buildRequestText(req: CapturedRequest): String = buildString {
        appendLine("=== REQUEST ===")
        appendLine("Time     : ${dateFmt.format(Date(req.timestamp))}")
        appendLine("Method   : ${req.method}")
        appendLine("URL      : ${req.url}")
        appendLine("Endpoint : ${req.endpoint}")
        appendLine()
        appendLine("--- Headers ---")
        appendLine(req.headersAsString())
        appendLine()
        appendLine("--- Body ---")
        appendLine(req.bodyText ?: "(empty)")
        appendLine()
        appendLine("--- Hex ---")
        appendLine(req.bodyHex ?: "(empty)")
    }

    fun buildResponseText(req: CapturedRequest, res: CapturedResponse): String = buildString {
        appendLine("=== RESPONSE ===")
        appendLine("Time     : ${dateFmt.format(Date(res.timestamp))}")
        appendLine("Status   : ${res.statusCode} ${res.statusMessage}")
        appendLine("Duration : ${res.durationMs}ms")
        appendLine()
        appendLine("--- Headers ---")
        appendLine(res.headersAsString())
        appendLine()
        appendLine("--- Body ---")
        appendLine(res.bodyText ?: "(empty)")
        appendLine()
        appendLine("--- Hex ---")
        appendLine(res.bodyHex ?: "(empty)")
    }

    fun buildUnifiedLog(req: CapturedRequest, res: CapturedResponse?): String = buildString {
        appendLine("=" .repeat(60))
        appendLine("ENDPOINT : ${req.endpoint}")
        appendLine("=" .repeat(60))
        appendLine(buildRequestText(req))
        res?.let { appendLine(buildResponseText(req, it)) }
        appendLine()
    }

    fun exportAll(context: Context, requests: List<CapturedRequest>, responses: Map<String, CapturedResponse>): File {
        val dir = File(context.getExternalFilesDir(null), "exports").also { it.mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "h7sker_capture_$ts.txt").also { f ->
            f.bufferedWriter().use { w ->
                requests.forEach { req -> w.write(buildUnifiedLog(req, responses[req.id])) }
            }
        }
    }

    fun shareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Export Logs"))
    }
}
