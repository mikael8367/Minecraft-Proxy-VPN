import http from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(fileURLToPath(new URL(".", import.meta.url)), "public");
const port = Number(process.env.PORT || 10000);
const mime = { ".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".svg": "image/svg+xml" };

function send(res, status, body, type = "application/json; charset=utf-8") {
  res.writeHead(status, {
    "Content-Type": type,
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "X-Frame-Options": "DENY",
    "Content-Security-Policy": "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; img-src 'self' data:",
    "Cache-Control": "no-store"
  });
  res.end(type.startsWith("application/json") ? JSON.stringify(body) : body);
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", "http://localhost");
  if (req.method === "GET" && url.pathname === "/api/health") {
    return send(res, 200, { ok: true, service: "Minecraft Proxy VPN API", version: "0.1.0", mode: "diagnostics-only", vpnTunnelActive: false, time: new Date().toISOString() });
  }
  if (req.method === "GET" && url.pathname === "/api/status") {
    return send(res, 200, {
      ok: true,
      api: "online",
      proxy: "not-configured",
      vpn: "not-configured",
      message: "API funcionando. O túnel VPN real ainda não está configurado."
    });
  }
  if (req.method !== "GET" && req.method !== "HEAD") {
    return send(res, 405, { ok: false, error: "Método não permitido." });
  }

  let pathname = decodeURIComponent(url.pathname);
  if (pathname === "/") pathname = "/index.html";
  const safePath = normalize(pathname).replace(/^([/\\]|\.\.(?:[/\\]|$))+/, "");
  const filePath = join(root, safePath);
  if (!filePath.startsWith(root)) return send(res, 403, { ok: false, error: "Acesso negado." });
  try {
    const data = await readFile(filePath);
    return send(res, 200, data.toString("utf8"), mime[extname(filePath)] || "application/octet-stream");
  } catch {
    return send(res, 404, { ok: false, error: "Não encontrado." });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Minecraft Proxy VPN API listening on 0.0.0.0:${port}`);
});