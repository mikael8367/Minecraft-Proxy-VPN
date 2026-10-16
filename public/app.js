const $ = (id) => document.getElementById(id);
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