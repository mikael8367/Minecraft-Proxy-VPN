package com.mikael8367.minecraftproxyvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TunnelVpnService : VpnService() {
    companion object {
        const val EXTRA_TOKEN = "token"
        const val EXTRA_ROLE = "role"
        const val ACTION_STOP = "com.mikael8367.minecraftproxyvpn.STOP"
        private const val CHANNEL = "minecraft_vpn"
        private const val TAG = "MinecraftVPN"
    }

    private var tun: ParcelFileDescriptor? = null
    private var socket: WebSocket? = null
    private var client: OkHttpClient? = null
    private val running = AtomicBoolean(false)
    private var writer: FileOutputStream? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }
        val token = intent?.getStringExtra(EXTRA_TOKEN)
        val role = intent?.getStringExtra(EXTRA_ROLE) ?: "member"
        if (token.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        stopTunnel()
        startForeground(19, notification("Conectando à rede privada..."))
        startTunnel(token, role)
        return START_NOT_STICKY
    }

    private fun startTunnel(token: String, role: String) {
        val local = if (role == "owner") "10.77.0.1" else "10.77.0.2"
        val remote = if (role == "owner") "10.77.0.2" else "10.77.0.1"
        try {
            tun = Builder()
                .setSession("Minecraft VPN")
                .setMtu(1400)
                .addAddress(local, 32)
                .addRoute(remote, 32)
                .establish() ?: throw IllegalStateException("Android não criou a interface VPN")
            writer = FileOutputStream(tun!!.fileDescriptor)
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível iniciar a interface VPN", e)
            stopTunnel()
            stopSelf()
            return
        }

        val http = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        client = http
        val request = Request.Builder()
            .url("wss://minecraft-proxy-vpn.onrender.com/tunnel")
            .header("Authorization", "Bearer $token")
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                running.set(true)
                updateNotification("Túnel conectado — rede privada ativa")
                startReader()
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    synchronized(this@TunnelVpnService) {
                        writer?.write(bytes.toByteArray())
                        writer?.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao entregar pacote à interface VPN", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Conexão do túnel falhou: HTTP ${response?.code}", t)
                updateNotification("Túnel desconectado — abra o app para tentar novamente")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                running.set(false)
                updateNotification("Túnel desconectado")
            }
        })
    }

    private fun startReader() {
        Thread({
            val descriptor = tun ?: return@Thread
            val input = FileInputStream(descriptor.fileDescriptor)
            val buffer = ByteArray(32767)
            try {
                while (running.get()) {
                    val count = input.read(buffer)
                    if (count <= 0) continue
                    val frame = ByteString.of(*buffer.copyOf(count))
                    val accepted = socket?.send(frame) ?: false
                    if (!accepted) Log.w(TAG, "Pacote descartado: WebSocket indisponível")
                }
            } catch (e: Exception) {
                if (running.get()) Log.e(TAG, "Leitura da interface VPN interrompida", e)
            } finally {
                try { input.close() } catch (_: Exception) {}
            }
        }, "vpn-tun-reader").apply { isDaemon = true }.start()
    }

    private fun stopTunnel() {
        running.set(false)
        try { socket?.close(1000, "Desconectando") } catch (_: Exception) {}
        socket = null
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        writer = null
        try { client?.dispatcher?.executorService?.shutdown() } catch (_: Exception) {}
        client = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun notification(message: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Minecraft VPN", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setContentTitle("Minecraft VPN")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(19, notification(message))
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }
}
