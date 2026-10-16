package com.mikael8367.minecraftproxyvpn

import android.app.Activity
import androidx.activity.ComponentActivity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var room: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var status: TextView
    private var pendingToken: String? = null
    private var pendingRole: String = "member"

    private val vpnPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingToken?.let { launchVpn(it, pendingRole) }
        else setStatus("Permissão VPN não concedida.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 32, 28, 24)
        }
        val scroll = ScrollView(this).apply { addView(page) }
        val title = TextView(this).apply {
            text = "Minecraft VPN"
            textSize = 28f
        }
        status = TextView(this).apply {
            text = "Crie uma rede no celular anfitrião e entre nela pelo outro celular."
            textSize = 15f
            setPadding(0, 14, 0, 18)
        }
        room = field("Nome da rede")
        username = field("Seu apelido")
        password = field("Senha (mínimo 6 caracteres)")
        page.addView(title)
        page.addView(status)
        page.addView(room)
        page.addView(username)
        page.addView(password)
        page.addView(actionButton("Criar rede (anfitrião)") { submit("/api/networks/create", "owner") })
        page.addView(actionButton("Entrar na rede (amigo)") { submit("/api/networks/join", "member") })
        page.addView(actionButton("Desconectar VPN") {
            startService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_STOP))
            setStatus("Desconexão solicitada.")
        })
        val note = TextView(this).apply {
            text = "IP virtual do anfitrião: 10.77.0.1\nIP virtual do amigo: 10.77.0.2\n\nEsta versão inicial retransmite pacotes pelo servidor. A descoberta automática de LAN pode não funcionar; use o IP virtual e a porta do jogo quando suportado."
            textSize = 14f
            setPadding(0, 20, 0, 0)
        }
        page.addView(note)
        setContentView(scroll)
    }

    private fun field(hintText: String) = EditText(this).apply {
        hint = hintText
        singleLine = true
        textSize = 16f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun actionButton(label: String, click: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun submit(path: String, role: String) {
        val name = room.text.toString().trim()
        val nick = username.text.toString().trim()
        val pass = password.text.toString()
        if (name.length < 3 || nick.length < 2 || pass.length < 6) {
            setStatus("Preencha rede (3+ caracteres), apelido (2+) e senha (6+).")
            return
        }
        setStatus("Conectando à API...")
        executor.execute {
            try {
                val conn = (URL("https://minecraft-proxy-vpn.onrender.com$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("name", name).put("username", nick).put("password", pass).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (conn.responseCode !in 200..299 || !json.optBoolean("ok")) {
                    runOnUiThread { setStatus(json.optString("error", "Falha ao entrar na rede.")) }
                    conn.disconnect()
                    return@execute
                }
                val token = json.getString("token")
                conn.disconnect()
                runOnUiThread {
                    pendingToken = token
                    pendingRole = role
                    val permission = VpnService.prepare(this)
                    if (permission != null) vpnPermission.launch(permission)
                    else launchVpn(token, role)
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Erro de conexão: ${e.message ?: "verifique a internet"}") }
            }
        }
    }

    private fun launchVpn(token: String, role: String) {
        val intent = Intent(this, TunnelVpnService::class.java)
            .putExtra(TunnelVpnService.EXTRA_TOKEN, token)
            .putExtra(TunnelVpnService.EXTRA_ROLE, role)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        setStatus("VPN iniciada. Aguardando o túnel...")
    }

    private fun setStatus(message: String) {
        if (::status.isInitialized) status.text = message
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
