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
import android.graphics.drawable.GradientDrawable
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
    private lateinit var visibility: Spinner
    private lateinit var description: EditText
    private var isPublic = false
    private val navButtons = mutableListOf<TextView>()
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
        window.statusBarColor = Color.rgb(7, 15, 23)
        window.navigationBarColor = Color.rgb(7, 15, 23)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(20))
            background = rounded(Color.rgb(10, 27, 29), 0, Color.rgb(10, 27, 29))
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(TextView(this).apply {
            text = "⬡"; textSize = 25f; gravity = Gravity.CENTER
            setTextColor(green); typeface = Typeface.DEFAULT_BOLD
            background = rounded(Color.rgb(17, 64, 49), 15, Color.rgb(26, 122, 85))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) }
        })
        brand.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "MIKAEL VPN"; textSize = 17f; setTextColor(white)
                setTypeface(null, Typeface.BOLD); letterSpacing = 0.05f
            })
            addView(TextView(this@MainActivity).apply {
                text = "CONEXÕES PARA JOGAR JUNTO"; textSize = 9f
                setTextColor(green); setTypeface(null, Typeface.BOLD); letterSpacing = 0.08f
                setPadding(0, dp(3), 0, 0)
            })
        })
        header.addView(brand)
        header.addView(TextView(this).apply {
            text = "Sua rede.\nSeu jogo. Seus amigos."
            textSize = 26f; setTextColor(white); setTypeface(null, Typeface.BOLD)
            setLineSpacing(dp(1).toFloat(), 1.0f); setPadding(0, dp(20), 0, dp(4))
        })
        header.addView(TextView(this).apply {
            text = "Crie uma rede privada ou encontre uma rede pública."
            textSize = 13f; setTextColor(muted)
        })
        root.addView(header)

        val scroll = ScrollView(this).apply {
            isFillViewport = true; clipToPadding = false; setBackgroundColor(bg)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(22))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(10, 19, 29), 22, Color.rgb(24, 43, 54))
        }
        navButtons.clear()
        nav.addView(navButton("⌂\nInício", 0), LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navButton("▤\nRedes", 1), LinearLayout.LayoutParams(0, dp(58), 1f))
        nav.addView(navButton("◎\nPúblicas", 2), LinearLayout.LayoutParams(0, dp(58), 1f))
        val navOuter = LinearLayout(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(10))
            setBackgroundColor(bg)
            addView(nav, LinearLayout.LayoutParams(-1, -2))
        }
        root.addView(navOuter)
        setContentView(root)
    }

    private fun navButton(label: String, tab: Int) = TextView(this).apply {
        text = label; gravity = Gravity.CENTER; textSize = 12f
        setTypeface(null, Typeface.BOLD); setLineSpacing(dp(2).toFloat(), 1f)
        setPadding(dp(2), dp(5), dp(2), dp(5))
        background = rounded(Color.TRANSPARENT, 15, Color.TRANSPARENT)
        setOnClickListener { showTab(tab) }
        navButtons.add(this)
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        content.removeAllViews()
        when (tab) {
            0 -> showHome()
            1 -> showNetworks()
            else -> showPublicNetworks()
        }
        navButtons.forEachIndexed { index, button ->
            val selected = index == tab
            button.setTextColor(if (selected) green else muted)
            button.background = rounded(
                if (selected) Color.rgb(16, 55, 44) else Color.TRANSPARENT,
                15,
                if (selected) Color.rgb(28, 115, 81) else Color.TRANSPARENT
            )
        }
    }

    private fun showHome() {
        content.addView(sectionTitle("Início", "Crie sua própria rede ou entre na rede de um amigo."))
        val card = card()
        card.addView(label("NOME DA REDE"))
        room = field("Ex.: Mundo do Mikael")
        card.addView(room)
        card.addView(label("DESCRIÇÃO DA REDE (OPCIONAL)"))
        description = EditText(this).apply {
            hint = "Ex.: Mundo survival com amigos"
            setTextColor(white); setHintTextColor(Color.rgb(116, 139, 153))
            textSize = 14f
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 2; maxLines = 4; gravity = Gravity.TOP
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(180))
            background = rounded(Color.rgb(10, 22, 32), 14, Color.rgb(37, 68, 77))
            layoutParams = LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(9) }
        }
        card.addView(description)
        card.addView(label("TIPO DE REDE"))
        visibility = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Privada — exige senha", "Pública — sem senha"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(Color.rgb(33, 44, 60))
        }
        card.addView(visibility, LinearLayout.LayoutParams(-1, dp(48)))
        visibility.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (::password.isInitialized) {
                    password.visibility = if (position == 0) View.VISIBLE else View.GONE
                    if (position == 1) password.setText("")
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
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
                        android.app.AlertDialog.Builder(this)
                            .setMessage("Deseja entrar nesta rede como amigo?")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Entrar") { _, _ -> submit("/api/networks/join", "member", item.optString("visibility") == "public") }.show()
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
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
        }
        val name = field("Nome da rede").apply { setText(item.optString("name")) }
        val nick = field("Seu apelido").apply { setText(item.optString("nick")) }
        val pass = field("Senha (vazia para rede pública)", true).apply { setText(item.optString("password")) }
        val desc = EditText(this).apply {
            hint = "Descrição da rede (máx. 120 caracteres)"
            setText(item.optString("description"))
            setTextColor(white); setHintTextColor(Color.rgb(116,139,153)); textSize = 14f
            minLines = 2; maxLines = 3; gravity = Gravity.TOP
            filters = arrayOf(android.text.InputFilter.LengthFilter(180))
            setPadding(dp(12),dp(10),dp(12),dp(10))
            background = rounded(Color.rgb(10,22,32),12,Color.rgb(37,68,77))
        }
        val visibilityChoice = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Privada — exige senha", "Pública — sem senha"))
            setSelection(if (item.optString("visibility") == "public") 1 else 0)
        }
        val maxUsers = field("Limite de jogadores (2 a 100)").apply {
            setText(item.optInt("maxUsers",20).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val version = field("Versão do Minecraft (informativa)").apply {
            setText(item.optString("gameVersion","Qualquer versão"))
        }
        val address = field("Endereço do servidor (opcional)").apply {
            setText(item.optString("serverAddress"))
        }
        val port = field("Porta do servidor (1 a 65535)").apply {
            setText(item.optInt("serverPort",25565).toString())
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val allowMembers = CheckBox(this).apply {
            text = "Permitir novas entradas"; setTextColor(white)
            isChecked = item.optBoolean("allowNewMembers",true)
        }
        val showMembers = CheckBox(this).apply {
            text = "Mostrar lista de participantes"; setTextColor(white)
            isChecked = item.optBoolean("showMembers",true)
        }
        listOf(
            label("NOME DA REDE"), name, label("SEU APELIDO"), nick,
            label("SENHA"), pass, label("DESCRIÇÃO"), desc,
            label("VISIBILIDADE"), visibilityChoice, label("LIMITE DE JOGADORES"), maxUsers,
            label("VERSÃO DO MINECRAFT"), version, label("ENDEREÇO DO SERVIDOR"), address,
            label("PORTA"), port, allowMembers, showMembers
        ).forEach { box.addView(it) }
        val scroll = ScrollView(this).apply { addView(box) }
        android.app.AlertDialog.Builder(this).setTitle("Configurações da rede")
            .setView(scroll).setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val n = name.text.toString().trim()
                val u = nick.text.toString().trim()
                val p = pass.text.toString()
                val limit = maxUsers.text.toString().toIntOrNull()
                val portNum = port.text.toString().toIntOrNull()
                if (n.length !in 3..40 || u.length !in 2..20 ||
                    (visibilityChoice.selectedItemPosition == 0 && p.length !in 6..128) ||
                    limit == null || limit !in 2..100 || portNum == null || portNum !in 1..65535) {
                    android.app.AlertDialog.Builder(this).setMessage("Confira nome (3–40), apelido (2–20), senha privada (6–128), limite (2–100) e porta (1–65535).")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                val settings = JSONObject()
                    .put("description", desc.text.toString().trim())
                    .put("password", p)
                    .put("visibility", if (visibilityChoice.selectedItemPosition == 1) "public" else "private")
                    .put("maxUsers", limit)
                    .put("allowNewMembers", allowMembers.isChecked)
                    .put("showMembers", showMembers.isChecked)
                    .put("gameVersion", version.text.toString().trim())
                    .put("serverAddress", address.text.toString().trim())
                    .put("serverPort", portNum)
                val authToken = item.optString("token")
                if (authToken.isBlank()) {
                    android.app.AlertDialog.Builder(this)
                        .setMessage("Esta rede não tem uma sessão salva para alterar as configurações do servidor. Entre nela novamente e tente outra vez.")
                        .setPositiveButton("OK", null).show()
                    return@setPositiveButton
                }
                executor.execute {
                    try {
                        val conn = (URL("https://minecraft-proxy-vpn.onrender.com/api/networks/settings").openConnection() as HttpURLConnection).apply {
                            requestMethod = "PUT"; connectTimeout = 15000; readTimeout = 15000; doOutput = true
                            setRequestProperty("Content-Type","application/json")
                            setRequestProperty("Authorization","Bearer $authToken")
                        }
                        conn.outputStream.use { it.write(settings.toString().toByteArray(Charsets.UTF_8)) }
                        val code = conn.responseCode
                        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val response = JSONObject(stream.bufferedReader().use { it.readText() })
                        runOnUiThread {
                            if (code !in 200..299 || !response.optBoolean("ok")) {
                                setStatus(response.optString("error","Não foi possível salvar as configurações."))
                            } else {
                                val list = readNetworks()
                                for (i in 0 until list.length()) {
                                    val obj = list.optJSONObject(i) ?: continue
                                    if (obj.optString("id") == item.optString("id")) {
                                        obj.put("name",n); obj.put("nick",u); obj.put("password",p)
                                        obj.put("description",desc.text.toString().trim())
                                        obj.put("visibility",if (visibilityChoice.selectedItemPosition == 1) "public" else "private")
                                        obj.put("maxUsers",limit); obj.put("allowNewMembers",allowMembers.isChecked)
                                        obj.put("showMembers",showMembers.isChecked); obj.put("gameVersion",version.text.toString().trim())
                                        obj.put("serverAddress",address.text.toString().trim()); obj.put("serverPort",portNum)
                                    }
                                }
                                saveNetworks(list)
                                setStatus("Configurações da rede salvas.")
                                showTab(1)
                            }
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        runOnUiThread { setStatus("Falha ao salvar: ${e.message ?: "verifique a conexão"}") }
                    }
                }
            }.show()
    }

    private fun showPublicNetworks() {
        content.addView(sectionTitle("Redes públicas", "Redes abertas que aceitam entrada sem senha."))
        val loading = TextView(this).apply { text = "Buscando redes públicas…"; textSize = 14f; setTextColor(muted) }
        content.addView(loading)
        executor.execute {
            try {
                val conn = URL("https://minecraft-proxy-vpn.onrender.com/api/networks/public").openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 15000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val items = json.optJSONArray("networks") ?: JSONArray()
                runOnUiThread {
                    content.removeView(loading)
                    if (items.length() == 0) {
                        val empty = card()
                        empty.addView(TextView(this).apply { text = "Nenhuma rede pública disponível"; textSize = 17f; setTextColor(white); gravity = Gravity.CENTER })
                        empty.addView(TextView(this).apply { text = "Crie uma rede pública pela aba Início para ela aparecer aqui."; textSize = 13f; setTextColor(muted); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
                        content.addView(empty)
                    } else for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val itemCard = card()
                        itemCard.addView(TextView(this).apply { text = "🌐  " + item.optString("name"); textSize = 18f; setTextColor(white); setTypeface(null, Typeface.BOLD) })
                        itemCard.addView(TextView(this).apply { text = item.optString("description", "Rede pública") + "\nJogadores: " + item.optInt("connectedCount") + "/" + item.optInt("maxUsers"); textSize = 13f; setTextColor(muted); setPadding(0, dp(8), 0, dp(8)) })
                        itemCard.addView(primaryButton("Entrar sem senha") {
                            showTab(0)
                            room.setText(item.optString("name"))
                            username.setText("Jogador")
                            password.setText("")
                            submit("/api/networks/join", "member", true)
                        })
                        content.addView(itemCard)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { loading.text = "Não foi possível carregar redes públicas. Verifique a conexão." }
            }
        }
    }

    private fun submit(path: String, role: String, publicJoin: Boolean = false) {
        val name = room.text.toString().trim()
        val nick = username.text.toString().trim().ifEmpty { if (publicJoin) "Jogador" else "" }
        val pass = password.text.toString()
        val publicNetwork = publicJoin || (path.endsWith("/create") && ::visibility.isInitialized && visibility.selectedItemPosition == 1)
        isPublic = publicNetwork
        val needsPassword = !publicNetwork
        if (name.length < 3 || nick.length < 2 || (needsPassword && pass.length < 6)) {
            setStatus(if (needsPassword) "Confira o nome (3+), apelido (2+) e a senha (6+)." else "Confira o nome da rede (3+) e o apelido (2+).")
            return
        }
        if (publicJoin && username.text.toString().isBlank()) username.setText(nick)
        setStatus("Conectando ao serviço…")
        executor.execute {
            try {
                val conn = (URL("https://minecraft-proxy-vpn.onrender.com$path").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 20000; readTimeout = 20000; doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("name", name).put("username", nick).put("password", pass)
                    .put("description", if (path.endsWith("/create")) description.text.toString().trim() else "")
                    .put("visibility", if (publicNetwork) "public" else "private").toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (code !in 200..299 || !json.optBoolean("ok")) {
                    runOnUiThread { setStatus(json.optString("error", "Não foi possível conectar à rede.")) }
                    conn.disconnect()
                    return@execute
                }
                val token = json.getString("token")
                conn.disconnect()
                runOnUiThread {
                    val saved = readNetworks()
                    var found = false
                    for (i in 0 until saved.length()) {
                        val obj = saved.optJSONObject(i) ?: continue
                        if (obj.optString("name").equals(name, true)) {
                            obj.put("nick", nick); obj.put("password", pass); obj.put("role", role)
                            obj.put("visibility", if (publicNetwork) "public" else "private")
                            obj.put("token", token)
                            found = true
                        }
                    }
                    if (!found) saved.put(JSONObject().put("id", System.currentTimeMillis().toString())
                        .put("name", name).put("nick", nick).put("password", pass).put("role", role)
                        .put("token", token)
                        .put("visibility", if (publicNetwork) "public" else "private"))
                    saveNetworks(saved)
                    pendingToken = token; pendingRole = role; pendingRoomName = name
                    pendingPassword = pass; pendingNick = nick
                    val permission = VpnService.prepare(this)
                    if (permission != null) vpnPermission.launch(permission) else launchVpn(token, role, name, pass)
                }
            } catch (e: Exception) {
                runOnUiThread { setStatus("Falha na conexão: ${e.message ?: "verifique a internet"}") }
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
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(17), dp(17), dp(17))
        background = rounded(Color.rgb(17, 30, 42), 22, Color.rgb(31, 58, 67))
        elevation = dp(2).toFloat()
        val params = LinearLayout.LayoutParams(-1, -2)
        params.bottomMargin = dp(16)
        layoutParams = params
    }

    private fun label(textValue: String) = TextView(this).apply {
        text = textValue; textSize = 11f; setTextColor(green); setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.08f; setPadding(dp(2), dp(10), 0, dp(3))
    }

    private fun field(hintText: String, secret: Boolean = false) = EditText(this).apply {
        hint = hintText; setSingleLine(true); textSize = 15f
        setTextColor(white); setHintTextColor(Color.rgb(116, 139, 153))
        setPadding(dp(14), dp(10), dp(14), dp(10))
        background = rounded(Color.rgb(10, 22, 32), 14, Color.rgb(37, 68, 77))
        minHeight = dp(50)
        if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        val p = LinearLayout.LayoutParams(-1, dp(52)); p.bottomMargin = dp(9); layoutParams = p
    }

    private fun primaryButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue; isAllCaps = false; textSize = 15f
        typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(5, 23, 17))
        background = rounded(green, 16, Color.rgb(69, 240, 167))
        elevation = dp(2).toFloat(); minHeight = dp(52)
        setPadding(dp(12), dp(10), dp(12), dp(10)); setOnClickListener { click() }
        val p = LinearLayout.LayoutParams(-1, dp(54)); p.topMargin = dp(12); layoutParams = p
    }

    private fun secondaryButton(textValue: String, click: () -> Unit) = Button(this).apply {
        text = textValue; isAllCaps = false; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
        setTextColor(white); background = rounded(Color.rgb(20, 40, 51), 16, Color.rgb(41, 79, 91))
        minHeight = dp(50); setPadding(dp(12), dp(10), dp(12), dp(10))
        setOnClickListener { click() }
        val p = LinearLayout.LayoutParams(-1, dp(52)); p.topMargin = dp(9); layoutParams = p
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int = Color.TRANSPARENT): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
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
