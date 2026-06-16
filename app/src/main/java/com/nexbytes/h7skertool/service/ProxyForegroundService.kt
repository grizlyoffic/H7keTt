package com.nexbytes.h7skertool.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.nexbytes.h7skertool.MainActivity
import com.nexbytes.h7skertool.model.CapturedRequest
import com.nexbytes.h7skertool.model.CapturedResponse
import kotlinx.coroutines.*

class ProxyForegroundService : Service() {
    private val TAG = "ProxyForegroundService"
    private val CHANNEL_ID = "h7sker_proxy"
    private val NOTIF_ID = 7001
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var proxy: ProxyServer? = null
    private lateinit var logging: LoggingManager

    companion object {
        const val ACTION_START = "com.nexbytes.h7skertool.START"
        const val ACTION_STOP  = "com.nexbytes.h7skertool.STOP"
        const val EXTRA_CLIENT_URL = "client_url"
        
        // Make these volatile for thread safety
        @Volatile
        var onCapture: ((CapturedRequest, CapturedResponse) -> Unit)? = null
        
        @Volatile
        var onLog: ((String) -> Unit)? = null
        
        @Volatile
        var savedMods: Map<String, String> = emptyMap()

        fun start(ctx: Context, clientUrl: String) {
            Log.d("ProxyService", "start() called with URL: $clientUrl")
            ctx.startForegroundService(Intent(ctx, ProxyForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CLIENT_URL, clientUrl)
            })
        }
        
        fun stop(ctx: Context) {
            Log.d("ProxyService", "stop() called")
            ctx.startService(Intent(ctx, ProxyForegroundService::class.java).apply { 
                action = ACTION_STOP 
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate()")
        logging = LoggingManager(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand() action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_CLIENT_URL) ?: "https://clientbp.ggpolarbear.com"
                startProxy(url)
            }
            ACTION_STOP -> stopProxy()
        }
        return START_STICKY
    }

    private fun startProxy(clientUrl: String) {
        Log.d(TAG, "startProxy() → $clientUrl")
        startForeground(NOTIF_ID, buildNotif("Capturing → $clientUrl"))
        
        // Log callback status
        Log.d(TAG, "onCapture callback is ${if (onCapture != null) "SET" else "NULL"}")
        Log.d(TAG, "onLog callback is ${if (onLog != null) "SET" else "NULL"}")
        
        proxy = ProxyServer(
            clientBaseUrl = clientUrl,
            scope = scope,
            savedMods = savedMods,
            onCapture = { req, res ->
                Log.d(TAG, "📥 ProxyServer.onCapture: ${req.method} ${req.endpoint}")
                // Call the static callback if set
                onCapture?.invoke(req, res) ?: Log.w(TAG, "⚠️ onCapture callback is null!")
                
                // Also log to file
                scope.launch {
                    try {
                        logging.logCapture(req, res)
                        logging.logBinary(req, res)
                    } catch (e: Exception) {
                        Log.e(TAG, "Logging error: ${e.message}")
                    }
                }
            },
            onLog = { msg ->
                Log.d(TAG, "📝 ProxyServer.onLog: $msg")
                onLog?.invoke(msg) ?: Log.w(TAG, "⚠️ onLog callback is null!")
            }
        )
        
        runCatching { 
            proxy!!.start()
            val msg = "Proxy started on 127.0.0.1:8080 → $clientUrl"
            onLog?.invoke(msg)
            Log.d(TAG, "✅ $msg")
        }.onFailure { e -> 
            val msg = "ERROR: ${e.message}"
            onLog?.invoke(msg)
            Log.e(TAG, "❌ $msg", e)
            stopSelf()
        }
    }

    private fun stopProxy() {
        Log.d(TAG, "stopProxy()")
        try {
            proxy?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping proxy: ${e.message}")
        }
        proxy = null
        onLog?.invoke("Proxy stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
        try { proxy?.stop() } catch (_: Exception) {}
        scope.cancel()
    }

    override fun onBind(i: Intent?): IBinder? = null

    private fun createChannel() {
        try {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "H7skER Capture", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Active proxy capture" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "createChannel error: ${e.message}")
        }
    }

    private fun buildNotif(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("H7skER TOOL")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
