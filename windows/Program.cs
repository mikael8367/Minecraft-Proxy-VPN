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
    private const string Api = "https://minecraft-proxy-vpn.onrender.com";
    private static readonly HttpClient Http = new() { Timeout = TimeSpan.FromSeconds(25) };

    private readonly Color bg = Color.FromArgb(7, 16, 24);
    private readonly Color sidebarColor = Color.FromArgb(10, 23, 32);
    private readonly Color panel = Color.FromArgb(16, 32, 43);
    private readonly Color panelAlt = Color.FromArgb(21, 43, 54);
    private readonly Color green = Color.FromArgb(48, 210, 133);
    private readonly Color white = Color.FromArgb(242, 249, 249);
    private readonly Color muted = Color.FromArgb(151, 174, 184);
    private readonly Color border = Color.FromArgb(36, 67, 78);

    private TextBox network = new(), nick = new(), password = new(), description = new();
    private ComboBox visibility = new();
    private Label status = new();
    private Panel sidebar = new(), body = new();
    private Button homeNav = new(), publicNav = new();
    private string? token;
    private string role = "member";
    private int page;

    public MainForm()
    {
        Text = "Mikael VPN";
        Width = 1000;
        Height = 760;
        MinimumSize = new Size(860, 650);
        StartPosition = FormStartPosition.CenterScreen;
        BackColor = bg;
        ForeColor = white;
        Font = new Font("Segoe UI", 10);

        var shell = new TableLayoutPanel {
            Dock = DockStyle.Fill, ColumnCount = 2, RowCount = 1,
            BackColor = bg, Margin = Padding.Empty, Padding = Padding.Empty
        };
        shell.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 218));
        shell.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        shell.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        Controls.Add(shell);

        BuildSidebar();
        shell.Controls.Add(sidebar, 0, 0);

        var main = new Panel { Dock = DockStyle.Fill, BackColor = bg, Padding = new Padding(0) };
        shell.Controls.Add(main, 1, 0);

        var header = new Panel { Dock = DockStyle.Top, Height = 112, BackColor = panel, Padding = new Padding(28, 20, 24, 14) };
        header.Controls.Add(new Label {
            Text = "MIKAEL VPN", ForeColor = green, Font = new Font("Segoe UI", 11, FontStyle.Bold),
            AutoSize = true, Location = new Point(28, 18)
        });
        header.Controls.Add(new Label {
            Text = "Jogue junto, de qualquer lugar", ForeColor = white,
            Font = new Font("Segoe UI", 21, FontStyle.Bold), AutoSize = true, Location = new Point(26, 42)
        });
        header.Controls.Add(new Label {
            Text = "REDE VIRTUAL • ANDROID + WINDOWS", ForeColor = muted,
            Font = new Font("Segoe UI", 8, FontStyle.Bold), AutoSize = true, Anchor = AnchorStyles.Top | AnchorStyles.Right,
            Location = new Point(Math.Max(400, Width - 490), 20)
        });
        main.Controls.Add(header);

        body = new Panel { Dock = DockStyle.Fill, AutoScroll = true, BackColor = bg, Padding = new Padding(26, 22, 26, 22) };
        main.Controls.Add(body);
        header.BringToFront();
        SetPage(0);
    }

    private void BuildSidebar()
    {
        sidebar = new Panel { Dock = DockStyle.Fill, BackColor = sidebarColor, Padding = new Padding(15, 20, 15, 18) };
        sidebar.Controls.Add(new Label {
            Text = "⬡", ForeColor = green, BackColor = Color.FromArgb(15, 60, 47),
            Font = new Font("Segoe UI Symbol", 26, FontStyle.Bold), TextAlign = ContentAlignment.MiddleCenter,
            Size = new Size(54, 54), Location = new Point(18, 20)
        });
        sidebar.Controls.Add(new Label {
            Text = "MIKAEL", ForeColor = white, Font = new Font("Segoe UI", 17, FontStyle.Bold),
            AutoSize = true, Location = new Point(82, 25)
        });
        sidebar.Controls.Add(new Label {
            Text = "VPN", ForeColor = green, Font = new Font("Segoe UI", 12, FontStyle.Bold),
            AutoSize = true, Location = new Point(83, 51)
        });
        var divider = new Panel { BackColor = border, Height = 1, Width = 180, Location = new Point(18, 92) };
        sidebar.Controls.Add(divider);

        homeNav = NavButton("⌂   Início", 0);
        homeNav.Location = new Point(12, 118);
        sidebar.Controls.Add(homeNav);
        publicNav = NavButton("◎   Redes públicas", 1);
        publicNav.Location = new Point(12, 170);
        sidebar.Controls.Add(publicNav);

        sidebar.Controls.Add(new Label {
            Text = "EXPERIÊNCIA EXPERIMENTAL", ForeColor = muted,
            Font = new Font("Segoe UI", 8, FontStyle.Bold), AutoSize = true, Location = new Point(18, 252)
        });
        sidebar.Controls.Add(new Label {
            Text = "A conexão de sala funciona, mas o túnel VPN Windows ainda não está completo para LAN do Minecraft.",
            ForeColor = muted, Font = new Font("Segoe UI", 9), MaximumSize = new Size(176, 0),
            AutoSize = true, Location = new Point(18, 277)
        });
        sidebar.Controls.Add(new Label {
            Text = "MIKAEL VPN  •  0.2", ForeColor = Color.FromArgb(104, 132, 143),
            Font = new Font("Segoe UI", 8), AutoSize = true, Anchor = AnchorStyles.Left | AnchorStyles.Bottom,
            Location = new Point(18, Height - 66)
        });
    }

    private Button NavButton(string title, int target)
    {
        var b = new Button {
            Text = title, TextAlign = ContentAlignment.MiddleLeft, Size = new Size(190, 42),
            FlatStyle = FlatStyle.Flat, BackColor = sidebarColor, ForeColor = muted,
            Font = new Font("Segoe UI", 10, FontStyle.Bold), Cursor = Cursors.Hand,
            Tag = target, Padding = new Padding(10, 0, 0, 0)
        };
        b.FlatAppearance.BorderSize = 0;
        b.FlatAppearance.MouseOverBackColor = panelAlt;
        b.Click += (_, _) => SetPage(target);
        return b;
    }

    private void SetPage(int target)
    {
        page = target;
        homeNav.BackColor = target == 0 ? Color.FromArgb(13, 62, 47) : sidebarColor;
        homeNav.ForeColor = target == 0 ? green : muted;
        publicNav.BackColor = target == 1 ? Color.FromArgb(13, 62, 47) : sidebarColor;
        publicNav.ForeColor = target == 1 ? green : muted;
        body.Controls.Clear();
        if (target == 0) BuildHome();
        else _ = BuildPublicNetworks();
    }

    private void BuildHome()
    {
        int y = 4;
        body.Controls.Add(new Label {
            Text = "Bem-vindo de volta", ForeColor = white, Font = new Font("Segoe UI", 22, FontStyle.Bold),
            AutoSize = true, Location = new Point(4, y)
        });
        y += 42;
        body.Controls.Add(new Label {
            Text = "Crie sua rede para convidar amigos ou entre na rede de outra pessoa.",
            ForeColor = muted, AutoSize = true, Location = new Point(6, y)
        });
        y += 36;

        var card = new Panel { BackColor = panel, Location = new Point(2, y), Size = new Size(Math.Max(570, body.ClientSize.Width - 34), 510), Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right };
        card.Controls.Add(new Panel { BackColor = green, Location = new Point(0, 0), Size = new Size(4, 510) });
        card.Controls.Add(new Label {
            Text = "Criar ou entrar em uma rede", ForeColor = white, Font = new Font("Segoe UI", 15, FontStyle.Bold),
            AutoSize = true, Location = new Point(24, 20)
        });
        card.Controls.Add(new Label {
            Text = "Escolha o nome e configure o acesso.", ForeColor = muted, AutoSize = true,
            Location = new Point(25, 49)
        });
        int fy = 88;
        AddField(card, "NOME DA REDE", network, ref fy, "Ex.: Mundo do Mikael");
        AddField(card, "SEU APELIDO", nick, ref fy, "Como você aparece na rede");
        card.Controls.Add(new Label {
            Text = "DESCRIÇÃO DA REDE", ForeColor = green, Font = new Font("Segoe UI", 9, FontStyle.Bold),
            AutoSize = true, Location = new Point(26, fy)
        });
        fy += 23;
        description.Location = new Point(24, fy);
        description.Size = new Size(Math.Max(330, card.Width - 50), 58);
        description.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        description.Multiline = true;
        description.MaxLength = 180;
        description.PlaceholderText = "Explique o objetivo, as regras ou a versão do Minecraft (opcional)";
        description.ScrollBars = ScrollBars.Vertical;
        StyleTextBox(description);
        card.Controls.Add(description);
        fy += 70;
        card.Controls.Add(new Label {
            Text = "VISIBILIDADE DA REDE", ForeColor = green, Font = new Font("Segoe UI", 9, FontStyle.Bold),
            AutoSize = true, Location = new Point(26, fy)
        });
        fy += 24;
        visibility = new ComboBox {
            Location = new Point(24, fy), Size = new Size(330, 34), DropDownStyle = ComboBoxStyle.DropDownList,
            BackColor = panelAlt, ForeColor = white, FlatStyle = FlatStyle.Flat,
            Font = new Font("Segoe UI", 10)
        };
        visibility.Items.AddRange(new object[] { "Privada — exige senha", "Pública — sem senha" });
        visibility.SelectedIndex = 0;
        visibility.SelectedIndexChanged += (_, _) => {
            password.Visible = visibility.SelectedIndex == 0;
            var lbl = card.Controls.OfType<Label>().FirstOrDefault(c => c.Tag?.ToString() == "password-label");
            if (lbl != null) lbl.Visible = password.Visible;
            if (!password.Visible) password.Clear();
        };
        card.Controls.Add(visibility);
        fy += 48;
        card.Controls.Add(new Label {
            Text = "SENHA DA REDE", ForeColor = green, Font = new Font("Segoe UI", 9, FontStyle.Bold),
            AutoSize = true, Location = new Point(26, fy), Tag = "password-label"
        });
        fy += 24;
        password.Location = new Point(24, fy);
        password.Size = new Size(Math.Max(330, card.Width - 50), 34);
        password.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        password.PlaceholderText = "Pelo menos 6 caracteres";
        password.UseSystemPasswordChar = true;
        StyleTextBox(password);
        card.Controls.Add(password);
        fy += 52;

        var create = MakeButton("＋   Criar rede", true);
        create.Location = new Point(24, fy);
        create.Size = new Size(220, 44);
        create.Click += async (_, _) => await Connect("/api/networks/create", "owner");
        card.Controls.Add(create);
        var join = MakeButton("↗   Entrar na rede", false);
        join.Location = new Point(258, fy);
        join.Size = new Size(220, 44);
        join.Click += async (_, _) => await Connect("/api/networks/join", "member");
        card.Controls.Add(join);
        body.Controls.Add(card);
        y += 450;

        status = new Label {
            Text = "●  Desconectado", ForeColor = green, Font = new Font("Segoe UI", 10, FontStyle.Bold),
            BackColor = panel, AutoSize = false, TextAlign = ContentAlignment.MiddleLeft,
            Location = new Point(2, y), Size = new Size(Math.Max(570, body.ClientSize.Width - 34), 52),
            Padding = new Padding(18, 0, 0, 0), Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right
        };
        body.Controls.Add(status);
        var leave = MakeButton("Sair da rede", false);
        leave.Location = new Point(2, y + 64);
        leave.Size = new Size(165, 40);
        leave.Click += async (_, _) => await Leave();
        body.Controls.Add(leave);
        body.Controls.Add(new Label {
            Text = "Nota: a versão atual conecta à sala do serviço. O adaptador/túnel VPN do Windows ainda precisa ser concluído para garantir que o Minecraft funcione como LAN.",
            ForeColor = muted, MaximumSize = new Size(Math.Max(500, body.ClientSize.Width - 45), 0), AutoSize = true,
            Location = new Point(4, y + 118)
        });
    }

    private async Task BuildPublicNetworks()
    {
        body.Controls.Clear();
        body.Controls.Add(new Label {
            Text = "Redes públicas", ForeColor = white, Font = new Font("Segoe UI", 22, FontStyle.Bold),
            AutoSize = true, Location = new Point(4, 4)
        });
        body.Controls.Add(new Label {
            Text = "Encontre redes abertas criadas por outros jogadores.", ForeColor = muted,
            AutoSize = true, Location = new Point(6, 46)
        });
        var refresh = MakeButton("↻  Atualizar lista", false);
        refresh.Location = new Point(4, 78);
        refresh.Size = new Size(180, 40);
        refresh.Click += async (_, _) => await BuildPublicNetworks();
        body.Controls.Add(refresh);
        var loading = new Label { Text = "Carregando redes públicas…", ForeColor = muted, AutoSize = true, Location = new Point(8, 136) };
        body.Controls.Add(loading);
        try
        {
            using var response = await Http.GetAsync(Api + "/api/networks/public");
            response.EnsureSuccessStatusCode();
            using var doc = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
            var root = doc.RootElement;
            var items = root.TryGetProperty("networks", out var value) && value.ValueKind == JsonValueKind.Array
                ? value.EnumerateArray().Select(e => e.Clone()).ToList()
                : new List<JsonElement>();
            body.Controls.Remove(loading);
            if (items.Count == 0)
            {
                body.Controls.Add(new Label {
                    Text = "Ainda não há redes públicas listadas. Você pode criar uma rede pública na aba Início.",
                    ForeColor = muted, AutoSize = true, MaximumSize = new Size(Math.Max(500, body.ClientSize.Width - 50), 0),
                    Location = new Point(8, 144)
                });
                return;
            }
            int y = 136;
            foreach (var item in items)
            {
                var name = item.TryGetProperty("name", out var n) ? n.GetString() ?? "Rede pública" : "Rede pública";
                var desc = item.TryGetProperty("description", out var d) ? d.GetString() ?? "Rede aberta" : "Rede aberta";
                var connected = item.TryGetProperty("connectedCount", out var c) ? c.ToString() : "0";
                var maxUsers = item.TryGetProperty("maxUsers", out var m) ? m.ToString() : "?";
                var card = new Panel {
                    BackColor = panel, Location = new Point(2, y),
                    Size = new Size(Math.Max(540, body.ClientSize.Width - 34), 104),
                    Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right
                };
                card.Controls.Add(new Panel { BackColor = green, Location = new Point(0, 0), Size = new Size(4, 104) });
                card.Controls.Add(new Label {
                    Text = name, ForeColor = white, Font = new Font("Segoe UI", 12, FontStyle.Bold),
                    AutoSize = true, Location = new Point(20, 15)
                });
                card.Controls.Add(new Label {
                    Text = $"{desc}   •   Jogadores: {connected}/{maxUsers}",
                    ForeColor = muted, AutoSize = true, Location = new Point(21, 44)
                });
                var enter = MakeButton("Entrar", true);
                enter.Size = new Size(112, 38);
                enter.Location = new Point(card.Width - 132, 31);
                enter.Anchor = AnchorStyles.Top | AnchorStyles.Right;
                enter.Click += async (_, _) => {
                    network.Text = name;
                    if (string.IsNullOrWhiteSpace(nick.Text)) nick.Text = "Jogador";
                    password.Clear();
                    visibility.SelectedIndex = 1;
                    SetPage(0);
                    await Connect("/api/networks/join", "member", true);
                };
                card.Controls.Add(enter);
                body.Controls.Add(card);
                y += 118;
            }
        }
        catch (Exception ex)
        {
            loading.Text = "Não foi possível carregar as redes públicas. Verifique a conexão.";
            loading.ForeColor = Color.FromArgb(255, 176, 93);
        }
    }

    private void AddField(Control parent, string label, TextBox box, ref int y, string hint)
    {
        parent.Controls.Add(new Label {
            Text = label, ForeColor = green, Font = new Font("Segoe UI", 9, FontStyle.Bold),
            AutoSize = true, Location = new Point(26, y)
        });
        y += 23;
        box.Location = new Point(24, y);
        box.Size = new Size(Math.Max(330, parent.Width - 50), 34);
        box.Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right;
        box.PlaceholderText = hint;
        StyleTextBox(box);
        parent.Controls.Add(box);
        y += 48;
    }

    private void StyleTextBox(TextBox box)
    {
        box.BackColor = Color.FromArgb(9, 22, 31);
        box.ForeColor = white;
        box.BorderStyle = BorderStyle.FixedSingle;
        box.Font = new Font("Segoe UI", 10);
    }

    private Button MakeButton(string text, bool primary)
    {
        var b = new Button {
            Text = text, FlatStyle = FlatStyle.Flat,
            BackColor = primary ? green : panelAlt,
            ForeColor = primary ? Color.FromArgb(5, 23, 17) : white,
            Font = new Font("Segoe UI", 10, FontStyle.Bold),
            Cursor = Cursors.Hand
        };
        b.FlatAppearance.BorderSize = 0;
        b.FlatAppearance.MouseOverBackColor = primary ? Color.FromArgb(81, 234, 160) : Color.FromArgb(31, 61, 74);
        return b;
    }

    private async Task Connect(string path, string requestedRole, bool publicJoin = false)
    {
        var n = network.Text.Trim();
        var u = nick.Text.Trim();
        var p = password.Text;
        var publicNetwork = publicJoin || (path.EndsWith("/create", StringComparison.Ordinal) && visibility.SelectedIndex == 1);
        var needsPassword = !publicNetwork;
        if (n.Length < 3 || u.Length < 2 || (needsPassword && p.Length < 6))
        {
            SetStatus(needsPassword
                ? "Confira nome da rede (3+), apelido (2+) e senha (6+)."
                : "Confira o nome da rede e um apelido com pelo menos 2 caracteres.");
            return;
        }
        try
        {
            SetStatus("Conectando ao serviço…");
            var json = JsonSerializer.Serialize(new {
                name = n, username = u, password = p,
                description = path.EndsWith("/create", StringComparison.Ordinal) ? description.Text.Trim() : "",
                visibility = publicNetwork ? "public" : "private"
            });
            using var response = await Http.PostAsync(Api + path, new StringContent(json, Encoding.UTF8, "application/json"));
            var raw = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(raw);
            var root = doc.RootElement;
            if (!response.IsSuccessStatusCode || !root.TryGetProperty("ok", out var ok) || !ok.GetBoolean())
            {
                SetStatus(root.TryGetProperty("error", out var err) ? err.GetString() ?? "Falha ao entrar." : "Falha ao entrar.");
                return;
            }
            token = root.GetProperty("token").GetString();
            role = requestedRole;
            SetStatus(requestedRole == "owner"
                ? (publicNetwork ? "Rede pública criada. A conexão Minecraft ainda é experimental." : "Rede privada criada. A conexão Minecraft ainda é experimental.")
                : "Sala conectada. O túnel VPN Windows ainda não está completo para LAN do Minecraft.");
        }
        catch (Exception ex) { SetStatus("Erro de conexão: " + ex.Message); }
    }

    private async Task Leave()
    {
        if (string.IsNullOrWhiteSpace(token)) { SetStatus("Você não está conectado a uma rede."); return; }
        try
        {
            using var req = new HttpRequestMessage(HttpMethod.Post, Api + "/api/networks/leave");
            req.Headers.Authorization = new AuthenticationHeaderValue("Bearer", token);
            using var res = await Http.SendAsync(req);
            token = null;
            SetStatus(res.IsSuccessStatusCode ? "Você saiu da rede." : "Não foi possível sair da rede.");
        }
        catch (Exception ex) { SetStatus("Erro ao sair: " + ex.Message); }
    }

    private void SetStatus(string text)
    {
        if (status.InvokeRequired) status.Invoke(() => status.Text = "●  " + text);
        else status.Text = "●  " + text;
    }
}