using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace MikaelVPN;

internal static class Program
{
    [STAThread]
    static void Main()
    {
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm());
    }
}

internal sealed class MainForm : Form
{
    const string Api = "https://minecraft-proxy-vpn.onrender.com";
    static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(25) };
    readonly Color bg = Color.FromArgb(12, 18, 28), panel = Color.FromArgb(23, 32, 46), green = Color.FromArgb(48, 210, 133), white = Color.FromArgb(244, 248, 252), muted = Color.FromArgb(163, 177, 195);
    TextBox network = new(), nick = new(), password = new();
    Label status = new();
    string? token;
    string role = "member";

    public MainForm()
    {
        Text = "Mikael VPN";
        Width = 760; Height = 720; MinimumSize = new Size(650, 620);
        BackColor = bg; ForeColor = white; Font = new Font("Segoe UI", 10);
        var shell = new TableLayoutPanel { Dock = DockStyle.Fill, ColumnCount = 1, RowCount = 3, Padding = new Padding(24), BackColor = bg };
        shell.RowStyles.Add(new RowStyle(SizeType.Absolute, 105));
        shell.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        shell.RowStyles.Add(new RowStyle(SizeType.Absolute, 65));
        Controls.Add(shell);
        var header = new Panel { Dock = DockStyle.Fill, BackColor = panel, Padding = new Padding(18) };
        header.Controls.Add(new Label { Text = "◆  MIKAEL VPN", ForeColor = green, Font = new Font("Segoe UI", 12, FontStyle.Bold), AutoSize = true, Location = new Point(16, 12) });
        header.Controls.Add(new Label { Text = "Jogue junto, de qualquer lugar", ForeColor = white, Font = new Font("Segoe UI", 20, FontStyle.Bold), AutoSize = true, Location = new Point(16, 42) });
        shell.Controls.Add(header, 0, 0);
        var body = new Panel { Dock = DockStyle.Fill, Padding = new Padding(8, 18, 8, 8), AutoScroll = true };
        shell.Controls.Add(body, 0, 1);
        int y = 10;
        body.Controls.Add(new Label { Text = "Início", ForeColor = white, Font = new Font("Segoe UI", 22, FontStyle.Bold), AutoSize = true, Location = new Point(8, y) }); y += 42;
        body.Controls.Add(new Label { Text = "Crie uma rede no seu PC ou entre na rede de um amigo.", ForeColor = muted, AutoSize = true, Location = new Point(10, y) }); y += 38;
        AddField(body, "NOME DA REDE", network, ref y, "Ex.: Mundo do Mikael");
        AddField(body, "SEU APELIDO", nick, ref y, "Seu nome na rede");
        AddField(body, "SENHA DA REDE", password, ref y, "Mínimo de 6 caracteres", true);
        var create = MakeButton("＋  Criar rede (anfitrião)", true); create.Location = new Point(10, y); create.Size = new Size(310, 46); create.Click += async (_, _) => await Connect("/api/networks/create", "owner"); body.Controls.Add(create);
        var join = MakeButton("↗  Entrar na rede (amigo)", false); join.Location = new Point(332, y); join.Size = new Size(310, 46); join.Click += async (_, _) => await Connect("/api/networks/join", "member"); body.Controls.Add(join); y += 64;
        status.Text = "Desconectado"; status.ForeColor = green; status.Location = new Point(10, y); status.Size = new Size(630, 45); body.Controls.Add(status); y += 52;
        var leave = MakeButton("Sair da rede", false); leave.Location = new Point(10, y); leave.Size = new Size(180, 42); leave.Click += async (_, _) => await Leave(); body.Controls.Add(leave); y += 55;
        body.Controls.Add(new Label { Text = "Estado experimental: o servidor oferece salas e retransmissão WebSocket, mas ainda não há adaptador VPN para Windows. Esta versão não cria sozinha uma LAN funcional para o Minecraft.", ForeColor = muted, AutoSize = false, MaximumSize = new Size(640, 0), Size = new Size(640, 65), Location = new Point(10, y) });
        var footer = new Panel { Dock = DockStyle.Fill, BackColor = panel };
        footer.Controls.Add(new Label { Text = "MIKAEL VPN  •  Windows 10/11", ForeColor = muted, AutoSize = true, Location = new Point(16, 20) });
        shell.Controls.Add(footer, 0, 2);
    }

    void AddField(Control parent, string label, TextBox box, ref int y, string hint, bool secret = false)
    {
        parent.Controls.Add(new Label { Text = label, ForeColor = green, Font = new Font("Segoe UI", 9, FontStyle.Bold), AutoSize = true, Location = new Point(10, y) }); y += 23;
        box.Location = new Point(10, y); box.Size = new Size(630, 34); box.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        box.PlaceholderText = hint; box.BackColor = panel; box.ForeColor = white; box.BorderStyle = BorderStyle.FixedSingle;
        if (secret) box.UseSystemPasswordChar = true;
        parent.Controls.Add(box); y += 48;
    }

    Button MakeButton(string text, bool primary)
    {
        var b = new Button { Text = text, FlatStyle = FlatStyle.Flat, BackColor = primary ? green : Color.FromArgb(51, 67, 86), ForeColor = primary ? bg : white, Font = new Font("Segoe UI", 10, FontStyle.Bold), Cursor = Cursors.Hand };
        b.FlatAppearance.BorderSize = 0; return b;
    }

    async Task Connect(string path, string requestedRole)
    {
        var n = network.Text.Trim(); var u = nick.Text.Trim(); var p = password.Text;
        if (n.Length < 3 || u.Length < 2 || p.Length < 6) { SetStatus("Preencha nome da rede, apelido e senha (mínimo 6 caracteres)."); return; }
        try
        {
            SetStatus("Conectando ao serviço...");
            var json = JsonSerializer.Serialize(new { name = n, username = u, password = p });
            using var response = await Http.PostAsync(Api + path, new StringContent(json, Encoding.UTF8, "application/json"));
            var raw = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(raw);
            var root = doc.RootElement;
            if (!response.IsSuccessStatusCode || !root.GetProperty("ok").GetBoolean())
            {
                SetStatus(root.TryGetProperty("error", out var err) ? err.GetString() ?? "Falha ao entrar." : "Falha ao entrar."); return;
            }
            token = root.GetProperty("token").GetString(); role = requestedRole;
            SetStatus(requestedRole == "owner" ? "Rede criada. Serviço de sala conectado; túnel VPN Windows ainda não disponível." : "Entrou na rede. Serviço de sala conectado; túnel VPN Windows ainda não disponível.");
        }
        catch (Exception ex) { SetStatus("Erro de conexão: " + ex.Message); }
    }

    async Task Leave()
    {
        if (string.IsNullOrWhiteSpace(token)) { SetStatus("Você não está conectado a uma rede."); return; }
        try
        {
            using var req = new HttpRequestMessage(HttpMethod.Post, Api + "/api/networks/leave");
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            using var res = await Http.SendAsync(req);
            token = null; SetStatus(res.IsSuccessStatusCode ? "Você saiu da rede." : "Não foi possível sair da rede.");
        }
        catch (Exception ex) { SetStatus("Erro ao sair: " + ex.Message); }
    }

    void SetStatus(string text) { if (status.InvokeRequired) status.Invoke(() => status.Text = text); else status.Text = text; }
}