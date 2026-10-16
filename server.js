import http from "node:http";
import { readFile } from "node:fs/promises";
import { randomBytes, scrypt as scryptCallback, timingSafeEqual, createHash } from "node:crypto";
import { promisify } from "node:util";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";

const scrypt = promisify(scryptCallback);
const root = join(fileURLToPath(new URL(".", import.meta.url)), "public");
const port = Number(process.env.PORT || 10000);
const mime = { ".html": "text/html; charset=utf-8", ".css": "text/css; charset=utf-8", ".js": "text/javascript; charset=utf-8", ".svg": "image/svg+xml" };
// Starter implementation: rooms and sessions are in memory and reset when the service restarts.
const rooms = new Map();
const sessions = new Map();
const attempts = new Map();

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
function normalizeRoomName(value) { return String(value || "").trim().normalize("NFKC"); }
function roomKey(value) { return normalizeRoomName(value).toLocaleLowerCase("pt-BR"); }
function hashToken(token) { return createHash("sha256").update(token).digest("hex"); }
function getIp(req) { return String(req.headers["x-forwarded-for"] || req.socket.remoteAddress || "unknown").split(",")[0].trim(); }
function rateLimited(req) {
  const now = Date.now(), key = getIp(req), old = attempts.get(key) || [];
  const recent = old.filter(t => now - t < 60_000);
  recent.push(now); attempts.set(key, recent);
  return recent.length > 20;
}
async function readJson(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 8192) throw new Error("BODY_TOO_LARGE");
  }
  try { return JSON.parse(raw || "{}"); } catch { throw new Error("INVALID_JSON"); }
}
async function passwordRecord(password) {
  const salt = randomBytes(16);
  const derived = await scrypt(password, salt, 64);
  return { salt: salt.toString("hex"), hash: Buffer.from(derived).toString("hex") };
}
async function verifyPassword(password, record) {
  const derived = Buffer.from(await scrypt(password, Buffer.from(record.salt, "hex"), 64));
  const expected = Buffer.from(record.hash, "hex");
  return derived.length === expected.length && timingSafeEqual(derived, expected);
}
function createSession(room) {
  const token = randomBytes(32).toString("base64url");
  sessions.set(hashToken(token), { roomKey: room.key, roomName: room.name, createdAt: Date.now() });
  return token;
}
function getSession(req) {
  const auth = String(req.headers.authorization || "");
  if (!auth.startsWith("Bearer ")) return null;
  return sessions.get(hashToken(auth.slice(7))) || null;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || "/", "http://localhost");
  if (req.method === "GET" && url.pathname === "/api/health") {
    return send(res, 200, { ok: true, service: "Minecraft Proxy VPN API", version: "0.2.0", mode: "network-rooms", vpnTunnelActive: false, time: new Date().toISOString() });
  }
  if (req.method === "GET" && url.pathname === "/api/status") {
    return send(res, 200, { ok: true, api: "online", proxy: "not-configured", vpn: "not-configured", rooms: rooms.size, message: "API funcionando. Redes com nome e senha estão disponíveis; túnel VPN real ainda não configurado." });
  }

  if (req.method === "POST" && ["/api/networks/create", "/api/networks/join"].includes(url.pathname)) {
    if (rateLimited(req)) return send(res, 429, { ok: false, error: "Muitas tentativas. Aguarde um minuto e tente novamente." });
    let body;
    try { body = await readJson(req); } catch (e) {
      return send(res, e.message === "BODY_TOO_LARGE" ? 413 : 400, { ok: false, error: e.message === "BODY_TOO_LARGE" ? "Requisição muito grande." : "JSON inválido." });
    }
    const name = normalizeRoomName(body.name);
    const password = typeof body.password === "string" ? body.password : "";
    if (name.length < 3 || name.length > 40 || !/^[\p{L}\p{N} _.-]+$/u.test(name)) {
      return send(res, 400, { ok: false, error: "O nome deve ter de 3 a 40 caracteres (letras, números, espaços, ponto, _ ou -)." });
    }
    if (password.length < 6 || password.length > 128) {
      return send(res, 400, { ok: false, error: "A senha deve ter entre 6 e 128 caracteres." });
    }
    const key = roomKey(name);
    if (url.pathname.endsWith("/create")) {
      if (rooms.has(key)) return send(res, 409, { ok: false, error: "Já existe uma rede com esse nome. Escolha outro nome ou entre nela." });
      const room = { key, name, password: await passwordRecord(password), createdAt: Date.now(), members: 1 };
      rooms.set(key, room);
      return send(res, 201, { ok: true, message: "Rede criada. Guarde o nome e a senha para entrar novamente.", network: { name: room.name, members: room.members }, token: createSession(room) });
    }
    const room = rooms.get(key);
    if (!room || !(await verifyPassword(password, room.password))) {
      return send(res, 401, { ok: false, error: "Nome da rede ou senha incorretos." });
    }
    room.members += 1;
    return send(res, 200, { ok: true, message: "Você entrou na rede.", network: { name: room.name, members: room.members }, token: createSession(room) });
  }

  if (req.method === "GET" && url.pathname === "/api/networks/me") {
    const session = getSession(req);
    if (!session) return send(res, 401, { ok: false, error: "Entre em uma rede com nome e senha." });
    const room = rooms.get(session.roomKey);
    if (!room) return send(res, 404, { ok: false, error: "A rede não existe mais. Crie ou entre novamente." });
    return send(res, 200, { ok: true, network: { name: room.name, members: room.members } });
  }
  if (req.method === "POST" && url.pathname === "/api/networks/leave") {
    const session = getSession(req);
    if (!session) return send(res, 401, { ok: false, error: "Nenhuma sessão ativa." });
    sessions.delete(hashToken(String(req.headers.authorization).slice(7)));
    const room = rooms.get(session.roomKey);
    if (room) room.members = Math.max(0, room.members - 1);
    return send(res, 200, { ok: true, message: "Você saiu da rede." });
  }

  if (req.method !== "GET" && req.method !== "HEAD") return send(res, 405, { ok: false, error: "Método não permitido." });
  let pathname;
  try { pathname = decodeURIComponent(url.pathname); } catch { return send(res, 400, { ok: false, error: "Caminho inválido." }); }
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

server.listen(port, "0.0.0.0", () => console.log(`Minecraft Proxy VPN API listening on 0.0.0.0:${port}`));
