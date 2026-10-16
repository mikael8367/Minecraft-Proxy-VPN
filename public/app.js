const $ = (id) => document.getElementById(id);
const TOKEN_KEY = "minecraftProxyNetworkToken";
function setNetworkMessage(message, isError = false) {
  $("networkMessage").textContent = message;
  $("networkMessage").style.color = isError ? "#ff9a9a" : "";
}
function setActiveNetwork(name) {
  $("activeNetwork").hidden = !name;
  $("activeNetworkName").textContent = name || "";
}
async function networkAction(path) {
  const name = $("networkName").value.trim();
  const password = $("networkPassword").value;
  if (name.length < 3 || name.length > 40 || password.length < 6) {
    setNetworkMessage("Informe um nome de 3 a 40 caracteres e uma senha de pelo menos 6 caracteres.", true);
    return;
  }
  $("createNetwork").disabled = true;
  $("joinNetwork").disabled = true;
  try {
    const response = await fetch(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name, password })
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "Não foi possível completar a operação.");
    try { sessionStorage.setItem(TOKEN_KEY, data.token); } catch {}
    setActiveNetwork(data.network.name);
    setNetworkMessage(data.message + " Participantes nesta instância: " + data.network.members + ".");
    $("networkPassword").value = "";
  } catch (error) {
    setNetworkMessage(error.message || "Falha de conexão. Tente novamente.", true);
  } finally {
    $("createNetwork").disabled = false;
    $("joinNetwork").disabled = false;
  }
}
async function restoreNetwork() {
  let token = "";
  try { token = sessionStorage.getItem(TOKEN_KEY) || ""; } catch {}
  if (!token) return;
  try {
    const response = await fetch("/api/networks/me", { headers: { Authorization: "Bearer " + token }, cache: "no-store" });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || "Sessão expirada.");
    setActiveNetwork(data.network.name);
    setNetworkMessage("Sessão restaurada. Participantes nesta instância: " + data.network.members + ".");
  } catch {
    try { sessionStorage.removeItem(TOKEN_KEY); } catch {}
    setActiveNetwork("");
  }
}
$("createNetwork").addEventListener("click", () => networkAction("/api/networks/create"));
$("joinNetwork").addEventListener("click", () => networkAction("/api/networks/join"));
$("leaveNetwork").addEventListener("click", async () => {
  let token = "";
  try { token = sessionStorage.getItem(TOKEN_KEY) || ""; } catch {}
  try {
    await fetch("/api/networks/leave", { method: "POST", headers: { Authorization: "Bearer " + token } });
  } finally {
    try { sessionStorage.removeItem(TOKEN_KEY); } catch {}
    setActiveNetwork("");
    setNetworkMessage("Você saiu da rede.");
  }
});
async function refreshStatus() {
  $("headline").textContent = "Verificando API…";
  $("subhead").textContent = "Consultando o serviço";
  try {
    const response = await fetch("/api/status", { cache: "no-store" });
    if (!response.ok) throw new Error("HTTP " + response.status);
    const data = await response.json();
    $("headline").textContent = "API online";
    $("subhead").textContent = data.message;
    $("api").textContent = data.api === "online" ? "Online" : "Indisponível";
    $("vpn").textContent = "Não configurado";
    $("dot").style.background = "#5de39a";
  } catch {
    $("headline").textContent = "API indisponível";
    $("subhead").textContent = "Verifique o deploy e tente novamente.";
    $("api").textContent = "Offline";
    $("dot").style.background = "#ff7f7f";
  }
}
$("refresh").addEventListener("click", refreshStatus);
$("save").addEventListener("click", () => {
  const host = $("host").value.trim();
  const port = Number($("port").value);
  if (!host || host.length > 253 || /[^a-zA-Z0-9.:[\]-]/.test(host) || !Number.isInteger(port) || port < 1 || port > 65535) {
    $("saved").textContent = "Confira o endereço e a porta (1 a 65535).";
    return;
  }
  try { localStorage.setItem("minecraftProxyTarget", JSON.stringify({ host, port })); } catch {}
  $("saved").textContent = "Configuração salva somente neste navegador: " + host + ":" + port;
});
try {
  const target = JSON.parse(localStorage.getItem("minecraftProxyTarget") || "null");
  if (target) { $("host").value = target.host; $("port").value = target.port; $("saved").textContent = "Configuração local: " + target.host + ":" + target.port; }
} catch {}
refreshStatus();
restoreNetwork();
