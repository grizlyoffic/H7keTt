package com.nexbytes.h7skertool.service

import android.util.Log
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import com.nexbytes.h7skertool.utils.HexUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ProxyServer(
    private val clientBaseUrl: String,
    private val scope: CoroutineScope,
    private val savedMods: Map<String, String>,
    private val onCapture: (CapturedRequest, CapturedResponse) -> Unit,
    private val onLog: (String) -> Unit
) : NanoHTTPD("127.0.0.1", 8080) {

    private val TAG = "ProxyServer"

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun serve(session: IHTTPSession): Response {
        val method = session.method.name
        val path = session.uri
        val endpoint = extractEndpoint(path)
        val start = System.currentTimeMillis()
        onLog("→ $method $path")

        val reqHeaders = session.headers.toMutableMap()
        val bodyBytes: ByteArray? = try {
            val len = reqHeaders["content-length"]?.toLongOrNull() ?: 0L
            if (len > 0) ByteArray(len.toInt()).also { session.inputStream.read(it) } else null
        } catch (_: Exception) { null }

        // ============================================================
        // 1. APPLY REQUEST MODIFICATION (Body)
        // ============================================================
        val finalBody = applyRequestMod(endpoint, bodyBytes)
        
        // ============================================================
        // 2. APPLY HEADER MODIFICATION
        // ============================================================
        val modifiedHeaders = applyHeaderMod(endpoint, reqHeaders)

        val bodyText = finalBody?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
        val bodyHex = HexUtils.toHexDump(finalBody)

        val capturedReq = CapturedRequest(
            method = method, url = "$clientBaseUrl$path", endpoint = endpoint,
            headers = modifiedHeaders, body = finalBody, bodyText = bodyText, bodyHex = bodyHex
        )

        return try {
            // ============================================================
            // 3. FORWARD REQUEST WITH MODIFIED HEADERS AND BODY
            // ============================================================
            val realResp = forwardRequest(method, path, modifiedHeaders, finalBody)
            val duration = System.currentTimeMillis() - start
            
            // ============================================================
            // 4. APPLY RESPONSE MODIFICATION
            // ============================================================
            val (modifiedRespBytes, modifiedRespText, modifiedRespHex, modifiedRespHeaders) = 
                applyResponseMod(endpoint, realResp)

            val capturedRes = CapturedResponse(
                requestId = capturedReq.id, 
                statusCode = realResp.code,
                statusMessage = realResp.message, 
                endpoint = endpoint,
                headers = modifiedRespHeaders, 
                body = modifiedRespBytes,
                bodyText = modifiedRespText, 
                bodyHex = modifiedRespHex, 
                durationMs = duration
            )
            
            onLog("← ${realResp.code} $endpoint (${duration}ms) [${if (modifiedRespBytes != realResp.body?.bytes()) "MODIFIED" else "ORIGINAL"}]")
            scope.launch { onCapture(capturedReq, capturedRes) }

            // ============================================================
            // 5. BUILD RESPONSE WITH MODIFIED BODY
            // ============================================================
            val mime = modifiedRespHeaders["content-type"] ?: "application/octet-stream"
            val response = newFixedLengthResponse(
                Response.Status.lookup(realResp.code), 
                mime,
                modifiedRespBytes?.inputStream(), 
                (modifiedRespBytes?.size ?: 0).toLong()
            )
            
            // Add modified headers (skip content-length and transfer-encoding)
            modifiedRespHeaders.forEach { (k, v) ->
                if (!k.equals("content-length", true) && !k.equals("transfer-encoding", true)) {
                    response.addHeader(k, v)
                }
            }
            
            realResp.close()
            response
        } catch (e: IOException) {
            onLog("✗ Error: $endpoint — ${e.message}")
            val errRes = CapturedResponse(
                requestId = capturedReq.id, statusCode = 503, statusMessage = "Proxy Error",
                endpoint = endpoint, headers = emptyMap(), body = null,
                bodyText = e.message, bodyHex = null, durationMs = -1
            )
            scope.launch { onCapture(capturedReq, errRes) }
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Proxy error: ${e.message}")
        }
    }

    // ============================================================
    // FORWARD REQUEST WITH MODIFIED HEADERS
    // ============================================================
    private fun forwardRequest(
        method: String, 
        path: String, 
        headers: Map<String, String>, 
        body: ByteArray?
    ): okhttp3.Response {
        val url = "$clientBaseUrl$path"
        val ct = headers["content-type"]?.toMediaTypeOrNull()
        val reqBody = when {
            body != null && method !in listOf("GET", "HEAD") -> body.toRequestBody(ct)
            method !in listOf("GET", "HEAD") -> ByteArray(0).toRequestBody(ct)
            else -> null
        }
        val builder = Request.Builder().url(url)
        val host = clientBaseUrl.removePrefix("https://").removePrefix("http://").split("/").first()
        
        // Add all headers (including modified ones)
        headers.forEach { (k, v) ->
            if (k.lowercase() !in listOf("host","connection","transfer-encoding","content-length","keep-alive")) {
                runCatching { builder.addHeader(k, v) }
            }
        }
        builder.header("Host", host)
        return http.newCall(builder.method(method, reqBody).build()).execute()
    }

    // ============================================================
    // 1. REQUEST BODY MODIFICATION
    // ============================================================
    private fun applyRequestMod(endpoint: String, body: ByteArray?): ByteArray? {
        // Check if there's a mod for this endpoint
        val mod = savedMods[endpoint] ?: return body
        return runCatching { mod.toByteArray(Charsets.UTF_8) }.getOrDefault(body)
    }

    // ============================================================
    // 2. HEADER MODIFICATION
    // ============================================================
    private fun applyHeaderMod(endpoint: String, headers: MutableMap<String, String>): MutableMap<String, String> {
        val modKey = "${endpoint}_headers"
        val headerMod = savedMods[modKey]
        
        if (headerMod.isNullOrEmpty()) {
            return headers
        }
        
        // Parse header modifications
        // Format: "Header-Name: Header-Value" per line
        val modifiedHeaders = headers.toMutableMap()
        
        try {
            headerMod.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach
                
                val parts = trimmed.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    
                    // Check if header should be removed (value is empty or "null")
                    if (value.isEmpty() || value.equals("null", ignoreCase = true)) {
                        modifiedHeaders.remove(key)
                        onLog("  🗑️ Removed header: $key")
                    } else {
                        modifiedHeaders[key] = value
                        onLog("  ✏️ Modified header: $key: $value")
                    }
                }
            }
        } catch (e: Exception) {
            onLog("  ⚠️ Header mod parse error: ${e.message}")
        }
        
        return modifiedHeaders
    }

    // ============================================================
    // 3. RESPONSE BODY MODIFICATION
    // ============================================================
    private fun applyResponseMod(
        endpoint: String, 
        response: okhttp3.Response
    ): ResponseModResult {
        val modKey = "${endpoint}_response"
        val mod = savedMods[modKey]
        
        // Get original response data
        val originalBytes = response.body?.bytes() ?: return ResponseModResult(
            bytes = null,
            text = null,
            hex = null,
            headers = response.headers.toMap()
        )
        
        // If no mod, return original
        if (mod.isNullOrEmpty()) {
            return ResponseModResult(
                bytes = originalBytes,
                text = runCatching { String(originalBytes, Charsets.UTF_8) }.getOrNull(),
                hex = HexUtils.toHexDump(originalBytes),
                headers = response.headers.toMap()
            )
        }
        
        // Apply modification
        val modifiedBytes = runCatching { mod.toByteArray(Charsets.UTF_8) }.getOrDefault(originalBytes)
        val modifiedText = runCatching { String(modifiedBytes, Charsets.UTF_8) }.getOrNull()
        val modifiedHex = HexUtils.toHexDump(modifiedBytes)
        
        // Update content-length header
        val modifiedHeaders = response.headers.toMutableMap()
        modifiedHeaders["content-length"] = modifiedBytes.size.toString()
        
        onLog("  ✏️ Response modified for $endpoint (${originalBytes.size} → ${modifiedBytes.size} bytes)")
        
        return ResponseModResult(
            bytes = modifiedBytes,
            text = modifiedText,
            hex = modifiedHex,
            headers = modifiedHeaders
        )
    }

    // ============================================================
    // HELPER: Extract endpoint
    // ============================================================
    private fun extractEndpoint(path: String): String {
        val clean = path.split("?").first().trimStart('/')
        val first = clean.split("/").firstOrNull { it.isNotEmpty() } ?: return path
        return "/$first"
    }

    // ============================================================
    // DATA CLASS FOR RESPONSE MOD RESULT
    // ============================================================
    private data class ResponseModResult(
        val bytes: ByteArray?,
        val text: String?,
        val hex: String?,
        val headers: Map<String, String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ResponseModResult

            if (bytes != null) {
                if (other.bytes == null) return false
                if (!bytes.contentEquals(other.bytes)) return false
            } else if (other.bytes != null) return false
            if (text != other.text) return false
            if (hex != other.hex) return false
            if (headers != other.headers) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes?.contentHashCode() ?: 0
            result = 31 * result + (text?.hashCode() ?: 0)
            result = 31 * result + (hex?.hashCode() ?: 0)
            result = 31 * result + headers.hashCode()
            return result
        }
    }
}

// ============================================================
// EXTENSION: okhttp3.Headers to Map
// ============================================================
fun okhttp3.Headers.toMap(): MutableMap<String, String> {
    val map = mutableMapOf<String, String>()
    this.forEach { (key, value) ->
        map[key] = value
    }
    return map
}
