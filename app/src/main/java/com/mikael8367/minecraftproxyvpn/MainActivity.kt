package com.mikael8367.minecraftproxyvpn

import android.app.Activity
import androidx.activity.ComponentActivity
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var prefs: SharedPreferences
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var room: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private var currentTab = 0
    private var pendingToken: String? = null
    private var pendingRole = "member"
    private var pendingRoomName = ""
    private var pendingPassword = ""
    private var pendingNick = ""

    private val bg = Color.rgb(12, 18, 28)
    private val panel = Color.rgb(23, 32, 46)
    private val green = Color.rgb(48, 210, 133)
    private val muted = Color.rgb(163, 177, 195)
    private val white = Color.rgb(244, 248, 252)

    private val vpnPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) pendingToken?.let { launchVpn(it, pendingRole, pendingRoomName, pendingPassword) }
        else setStatus("Permissão VPN não concedida.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("saved_networks", MODE_PRIVATE)
        buildShell()
        showTab(0)
    }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(16))
            setBackgroundColor(panel)
        }
        header.addView(TextView(this).apply {
            text = "MINECRAFT VPN"; textSize = 12f; setTextColor(green); setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.16f
        })
        header.addView(TextView(this).apply {
            text = "Jogue junto, de qualquer lugar"; textSize = 23f; setTextColor(white)
            setTypeface(null, Typeface.BOLD); setPadding(0, dp(7), 0, 0)
        })
        root.addView(header)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setBackgroundColor(panel); setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        nav.addView(navButton("⌂\nInício", 0), LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navButton("▦\nRedes", 1), LinearLayout.LayoutParams(0, dp(58), 1f))
        root.addView(nav)
        setContentView(root)
    }

    private fun navButton(label: String, tab: Int) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 14f
        setTypeface(null, Typeface.BOLD); setPadding(0, dp(4), 0, dp(4))
        setOnClickListener { showTab(tab) }
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        content.removeAllViews()
        if (tab == 0) showHome() else showNetworks()
        // Highlight the selected tab through the accent color.
        val nav = root.getChildAt(2) as? LinearLayout
        if (nav != null) for (i in 0..1) (nav.getChildAt(i) as TextView).setTextColor(if (i == tab) green else muted)
    }

    private fun showHome() {
        content.addView(sectionTitle("Início", "Crie sua própria rede ou entre na rede de um amigo."))
        val card = card()
        card.addView(label("NOME DA REDE"))
        room = field("Ex.: Mundo do Mikael")
        card.addView(room)
        card.addView(label("SEU APELIDO"))
        username = field("Como você aparece na rede")
        card.addView(username)
        card.addView(label("SENHA DA REDE"))
        password = field("Mínimo de 6 caracteres", true)
        card.addView(password)
        card.addView(primaryButton("＋   Criar rede (anfitrião)") { submit("/api/networks/create", "owner") })
        card.addView(secondaryButton("↗   Entrar na rede (amigo)") { submit("/api/networks/join", "member") })
        content.addView(card)
        val statusCard = card()
        statusCard.addView(label("STATUS DA CONEXÃO"))
        status = TextView(this).apply {
            text = "Desconectado"; textSize = 15f; setTextColor(white); setPadding(0, dp(6), 0, dp(8))
        }
        statusCard.addView(status)
        statusCard.addView(secondaryButton("Desconectar VPN") {
            startService(Intent(this, TunnelVpnService::class.java).setAction(TunnelVpnService.ACTION_STOP))
            setStatus("Desconexão solicitada.")
        })
        content.addView(statusCard)
        content.addView(TextView(this).apply {
            text = "IP virtual do anfitrião: 10.77.0.1\nIP virtual do amigo: 10.77.0.2\n\nNota: esta versão inicial do túnel ainda é experimental; a conexão direta do Minecraft pode não funcionar em todos os cenários."
            textSize = 13f; setTextColor(muted); setPadding(dp(4), dp(14), dp(4), dp(8))
        })
    }

    private fun showNetworks() {
        content.addView(sectionTitle("Minhas redes", "Redes salvas neste celular. Toque em uma para ver as opções."))
        val list = readNetworks()
        if (list.length() == 0) {
            val empty = card()
            empty.addView(TextView(this).apply {
                text = "▦"; textSize = 36f; gravity = Gravity.CENTER; setTextColor(green)
            })
            empty.addView(TextView(this).apply {
                text = "Nenhuma rede salva ainda"; textSize = 17f; gravity = Gravity.CENTER
                setTextColor(white); setTypeface(null, Typeface.BOLD); setPadding(0, dp(8), 0, dp(6))
            })
            empty.addView(TextView(this).apply {
                text = "Crie uma rede ou entre em uma pela aba Início. Ela aparecerá aqui para você configurar."
                textSize = 13f; gravity = Gravity.CENTER; setTextColor(muted)
            })
            content.addView(empty)
            return
        }
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            val netCard = card()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            details.addView(TextView(this).apply {
                text = item.optString("name", "Rede"); textSize = 18f; setTextColor(white)
                setTypeface(null, Typeface.BOLD)
            })
            details.addView(TextView(this).apply {
                text = "Apelido: " + item.optString("nick", "Jogador"); textSize = 13f; setTextColor(muted)
                setPadding(0, dp(5), 0, 0)
            })
            row.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(this).apply { text = "⋮"; textSize = 26f; setTextColor(green); setPadding(dp(12), dp(4), dp(4), dp(4)) })
            row.setOnClickListener { networkOptions(item) }
            netCard.addView(row)
            netCard.addView(TextView(this).apply {
                text = "Toque para conectar, editar ou remover"; textSize = 12f; setTextColor(muted)
                setPadding(0, dp(10), 0, 0)
            })
            content.addView(netCard)
        }
        content.addView(secondaryButton("＋   Criar ou entrar em outra rede") { showTab(0) })
    }

    private fun networkOptions(item: JSONObject) {
        val options = arrayOf("Conectar nesta rede", "Configurar rede", "Remover rede salva")
        android.app.AlertDialog.Builder(this)
            .setTitle(item.optString("name", "Rede"))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        showTab(0)
                        room.setText(item.optString("name"))
                        username.setText(item.optString("nick"))
                        password.setText(item.optString("password"))
                        new android.app.AlertDialog.Builder(this)
                            .setMessage("Deseja entrar nesta rede como amigo?")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Entrar") { _, _ -> submit("/api/networks/join", "member") }.show()
                    }
                    1 -> editNetwork(item)
                    2 -> android.app.AlertDialog.Builder(this).setMessage("Remover esta rede da lista salva neste celular?")
                        .setNegativeButton("Cancelar", null).setPositiveButton("Remover") { _, _ ->
                            val list = readNetworks()
                            val updated = JSONArray()
                            for (i in 0 until list.length()) {
                                val obj = list.optJSONObject(i) ?: continue
                                if (obj.optString("id") != item.optString("id")) updated.put(obj)
                            }
                            saveNetworks(updated); showTab(1)
                        }.show()
                }
            }.show()
    }

    private fun editNetwork(item: JSONObject) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(8), dp(22), 0)
        }
        val name = field("Nome da rede").apply { setText(item.optString("name")) }
        val nick = field("Apelido").apply { setText(item.optString("nick")) }
        val pass = field("Senha").apply { setText(item.optString("password")) }
        box.addView(name); box.addView(nick); box.addView(pass)
        android.app.AlertDialog.Builder(this).setTitle("Configurar rede")
            .setView(box).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val list = readNetworks()
                for (i in 0 until list.length()) {
                    val obj = list.optJSONObject(i) ?: continue
                    if (obj.optString("id") == item.optString("id")) {
                        obj.put("name", name.text.toString().trim())
                        obj.put("nick", nick.text.toString().trim())
                        obj.put("password", pass.text.toString())
                    }
                }
                saveNetworks(list); showTab(1)
            }.show()
    }

    private fun submit(path: String, role: String) {
        val name = room.text.toString().trim()
        val nick = username.text.toString().trim()
        val pass = password.text.toString()
        if (name.length < 3 || nick.length < 2 || pass.length < 6) {
            setStatus("Preencha rede (3+ caracteres), apelido (2+) e senha (6+).")
            return
        }
        setStatus("Conectando à API…")
        executor.execute {
            try {
                val conn = (URL("https://minecraft-proxy-vpn.onrender.com$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 20000; readTimeout = 20000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("name", name).put("username", nick).put("password", pass).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (code !in 200..299 || !json.optBoolean("ok")) {
                    runOnUiThread { setStatus(json.optString("error", "Falha ao entrar na rede.")) }
                    conn.disconnect(); return@execute
                }
                val token = json.getString("token")
                conn.disconnect()
                runOnUiThread {
                    val saved = readNetworks()
                    var found = false
                    for (i in 0 until saved.length()) {
                        val obj = saved.optJSONObject(i) ?: continue
                        if (obj.optString("name").equals(name, true)) {
                            obj.put("nick", nick); obj.put("password", pass); obj.put("role", role); found = true
                        }
                    }
                    if (!found) saved.put(JSONObject().put("id", System.currentTimeMillis().toString())
                        .put("name", name).put("nick", nick).put("password", pass).put("role", role))
                    saveNetworks(saved)
                    pendingToken = token; pendingRole = role; pendingRoomName = name
                    pendingPassword = pass; pendingNick = nick
                    val permission = VpnService.prepare(this)
                    if (permission != null) vpnPermission.launch(permission) else launchVpn(token, role, name, pass)
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Erro de conexão: ${e.message ?: "verifique a internet"}") }
            }
        }
    }

    private fun launchVpn(token: String, role: String, roomName: String, passphrase: String) {
        val intent = Intent(this, TunnelVpnService::class.java)
            .putExtra(TunnelVpnService.EXTRA_TOKEN, token)
            .putExtra(TunnelVpnService.EXTRA_ROLE, role)
            .putExtra(TunnelVpnService.EXTRA_ROOM, roomName)
            .putExtra(TunnelVpnService.EXTRA_PASSWORD, passphrase)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        setStatus("VPN iniciada. Aguardando o túnel…")
    }

    private fun readNetworks(): JSONArray = try { JSONArray(prefs.getString("items", "[]")) } catch (_: Exception) { JSONArray() }
    private fun saveNetworks(items: JSONArray) { prefs.edit().putString("items", items.toString()).apply() }

    private fun sectionTitle(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(2), dp(2), dp(16))
        addView(TextView(this@MainActivity).apply { text = title; textSize = 25f; setTextColor(white); setTypeface(null, Typeface.BOLD) })
        addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 14f; setTextColor(muted); setPadding(0, dp(5), 0, 0) })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(panel)
        val params = LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(14); layoutParams = params
    }

    private fun label(textValue: String) = TextView(this).apply {
        text = textValue; textSize = 11f; setTextColor(green); setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.08f; setPadding(dp(2), dp(10), 0, dp(3))
    }

    private fun field(hintText: String, secret: Boolean = false) = EditText(this).apply {
        hint = hintText; setSingleLine(true); textSize = 15f; setTextColor(white); setHintTextColor(muted)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundColor(Color.rgb(33, 44, 60))
        if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        val p = LinearLayout.LayoutParams(-1, dp(50)); p.bottomMargin = dp(7); layoutParams = p
    }

    private fun primaryButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue; setTextColor(Color.rgb(7, 30, 21)); setBackgroundTintList(android.content.res.ColorStateList.valueOf(green))
        setOnClickListener { click() }
        val p = LinearLayout.LayoutParams(-1, dp(52)); p.topMargin = dp(12); layoutParams = p
    }

    private fun secondaryButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue; setTextColor(white); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(51, 67, 86)))
        setOnClickListener { click() }
        val p = LinearLayout.LayoutParams(-1, dp(50)); p.topMargin = dp(8); layoutParams = p
    }

    private fun setStatus(message: String) {
        if (::status.isInitialized) status.text = message
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
