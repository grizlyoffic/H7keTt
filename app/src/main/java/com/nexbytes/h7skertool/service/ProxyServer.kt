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

        // Apply saved modifications if any
        val finalBody = applyMod(endpoint, bodyBytes)

        val bodyText = finalBody?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
        val bodyHex = HexUtils.toHexDump(finalBody)

        val capturedReq = CapturedRequest(
            method = method, url = "$clientBaseUrl$path", endpoint = endpoint,
            headers = reqHeaders, body = finalBody, bodyText = bodyText, bodyHex = bodyHex
        )

        return try {
            val realResp = forwardRequest(method, path, reqHeaders, finalBody)
            val duration = System.currentTimeMillis() - start
            val respBytes = realResp.body?.bytes()
            val respText = respBytes?.let { runCatching { String(it, Charsets.UTF_8) }.getOrNull() }
            val respHex = HexUtils.toHexDump(respBytes)
            val respHeaders = mutableMapOf<String, String>()
            realResp.headers.forEach { (k, v) -> respHeaders[k] = v }

            val capturedRes = CapturedResponse(
                requestId = capturedReq.id, statusCode = realResp.code,
                statusMessage = realResp.message, endpoint = endpoint,
                headers = respHeaders, body = respBytes,
                bodyText = respText, bodyHex = respHex, durationMs = duration
            )
            onLog("← ${realResp.code} $endpoint (${duration}ms)")
            scope.launch { onCapture(capturedReq, capturedRes) }

            val mime = respHeaders["content-type"] ?: "application/octet-stream"
            val response = newFixedLengthResponse(
                Response.Status.lookup(realResp.code), mime,
                respBytes?.inputStream(), (respBytes?.size ?: 0).toLong()
            )
            respHeaders.forEach { (k, v) ->
                if (!k.equals("content-length", true) && !k.equals("transfer-encoding", true))
                    response.addHeader(k, v)
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

    private fun forwardRequest(method: String, path: String, headers: Map<String, String>, body: ByteArray?): okhttp3.Response {
        val url = "$clientBaseUrl$path"
        val ct = headers["content-type"]?.toMediaTypeOrNull()
        val reqBody = when {
            body != null && method !in listOf("GET", "HEAD") -> body.toRequestBody(ct)
            method !in listOf("GET", "HEAD") -> ByteArray(0).toRequestBody(ct)
            else -> null
        }
        val builder = Request.Builder().url(url)
        val host = clientBaseUrl.removePrefix("https://").removePrefix("http://").split("/").first()
        headers.forEach { (k, v) ->
            if (k.lowercase() !in listOf("host","connection","transfer-encoding","content-length","keep-alive")) {
                runCatching { builder.addHeader(k, v) }
            }
        }
        builder.header("Host", host)
        return http.newCall(builder.method(method, reqBody).build()).execute()
    }

    private fun applyMod(endpoint: String, body: ByteArray?): ByteArray? {
        val mod = savedMods[endpoint] ?: return body
        return runCatching { mod.toByteArray(Charsets.UTF_8) }.getOrDefault(body)
    }

    private fun extractEndpoint(path: String): String {
        val clean = path.split("?").first().trimStart('/')
        val first = clean.split("/").firstOrNull { it.isNotEmpty() } ?: return path
        return "/$first"
    }
}
