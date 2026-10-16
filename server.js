import http from "node:http";
import { WebSocketServer, WebSocket } from "ws";
import { readFile } from "node:fs/promises";
import { randomBytes, scrypt as scryptCallback, timingSafeEqual, createHash } from "node:crypto";
import { promisify } from "node:util";
import { extname, join, normalize } from "node:path";
import { fileURLToPath } from "node:url";
const scrypt=promisify(scryptCallback), root=join(fileURLToPath(new URL(".",import.meta.url)),"public"), port=Number(process.env.PORT||10000);
const mime={".html":"text/html; charset=utf-8",".css":"text/css; charset=utf-8",".js":"text/javascript; charset=utf-8",".svg":"image/svg+xml"};
const rooms=new Map(), sessions=new Map(), attempts=new Map();
function send(res,status,body,type="application/json; charset=utf-8"){res.writeHead(status,{"Content-Type":type,"X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer","X-Frame-Options":"DENY","Content-Security-Policy":"default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; img-src 'self' data:","Cache-Control":"no-store"});res.end(type.startsWith("application/json")?JSON.stringify(body):body)}
function norm(v){return String(v||"").trim().normalize("NFKC")} function key(v){return norm(v).toLocaleLowerCase("pt-BR")} function hash(v){return createHash("sha256").update(v).digest("hex")}
function ip(req){return String(req.headers["x-forwarded-for"]||req.socket.remoteAddress||"unknown").split(",")[0].trim()}
function limited(req){const now=Date.now(),k=ip(req),recent=(attempts.get(k)||[]).filter(t=>now-t<60000);recent.push(now);attempts.set(k,recent);return recent.length>25}
async function json(req){let raw="";for await(const c of req){raw+=c;if(raw.length>8192)throw Error("LARGE")}try{return JSON.parse(raw||"{}")}catch{throw Error("JSON")}}
async function pwRecord(password){const salt=randomBytes(16),derived=await scrypt(password,salt,64);return{salt:salt.toString("hex"),hash:Buffer.from(derived).toString("hex")}}
async function pwCheck(password,r){const got=Buffer.from(await scrypt(password,Buffer.from(r.salt,"hex"),64)),expected=Buffer.from(r.hash,"hex");return got.length===expected.length&&timingSafeEqual(got,expected)}
function session(req){const a=String(req.headers.authorization||"");if(!a.startsWith("Bearer "))return null;const token=a.slice(7),s=sessions.get(hash(token));if(s)s.lastSeen=Date.now();return s?{...s,tokenHash:hash(token)}:null}
function memberList(room){return [...room.members.values()].map(m=>({username:m.username,role:m.role,connected:Date.now()-m.lastSeen<120000,joinedAt:m.joinedAt}))}
function enter(room,username,role){const token=randomBytes(32).toString("base64url"),tokenHash=hash(token),now=Date.now();const m={username,role,joinedAt:now,lastSeen:now,tokenHash};room.members.set(tokenHash,m);sessions.set(tokenHash,{roomKey:room.key,username,role,joinedAt:now,lastSeen:now});return token}
function getRoom(req){const s=session(req);if(!s)return{error:[401,{ok:false,error:"Entre na rede com nome e senha."}]};const room=rooms.get(s.roomKey);if(!room)return{error:[404,{ok:false,error:"A rede não existe mais; entre novamente."}]};return{room,s}}
const server=http.createServer(async(req,res)=>{
 const url=new URL(req.url||"/","http://localhost");
 if(req.method==="GET"&&url.pathname==="/api/health")return send(res,200,{ok:true,service:"Minecraft Proxy VPN API",version:"0.3.0",mode:"password-protected-rooms-with-websocket-relay",relayAvailable:true,vpnTunnelActive:false,time:new Date().toISOString()});
 if(req.method==="GET"&&url.pathname==="/api/status")return send(res,200,{ok:true,api:"online",proxy:"not-configured",relay:"websocket-packet-relay-ready",vpn:"android-vpn-client-required",relayAvailable:true,vpnTunnelActive:false,rooms:rooms.size,message:"Retransmissão WebSocket autenticada disponível; o túnel VPN completo exige o cliente Android VpnService."});
 if(req.method==="POST"&&["/api/networks/create","/api/networks/join"].includes(url.pathname)){
  if(limited(req))return send(res,429,{ok:false,error:"Muitas tentativas. Aguarde um minuto."});
  let b;try{b=await json(req)}catch(e){return send(res,e.message==="LARGE"?413:400,{ok:false,error:e.message==="LARGE"?"Requisição muito grande.":"JSON inválido."})}
  const name=norm(b.name),password=typeof b.password==="string"?b.password:"",username=norm(b.username||"Jogador");
  if(name.length<3||name.length>40||!/^[\p{L}\p{N} _.-]+$/u.test(name))return send(res,400,{ok:false,error:"Nome da rede: 3–40 caracteres, letras, números, espaços, ponto, _ ou -."});
  if(password.length<6||password.length>128)return send(res,400,{ok:false,error:"A senha deve ter entre 6 e 128 caracteres."});
  if(username.length<2||username.length>20||!/^[\p{L}\p{N}_ -]+$/u.test(username))return send(res,400,{ok:false,error:"Nome de usuário: 2–20 caracteres."});
  const k=key(name);
  if(url.pathname.endsWith("/create")){
   if(rooms.has(k))return send(res,409,{ok:false,error:"Já existe uma rede com esse nome."});
   const room={key:k,name,password:await pwRecord(password),createdAt:Date.now(),ownerUsername:username,settings:{description:"Minha rede Minecraft",visibility:"private",maxUsers:20,allowNewMembers:true,showMembers:true,gameVersion:"Qualquer versão",serverAddress:"",serverPort:25565},members:new Map()};
   rooms.set(k,room);const token=enter(room,username,"owner");
   return send(res,201,{ok:true,message:"Rede criada.",network:{name:room.name,settings:room.settings,members:memberList(room)},token});
  }
  const room=rooms.get(k);if(!room||!(await pwCheck(password,room.password)))return send(res,401,{ok:false,error:"Nome da rede ou senha incorretos."});
  if(!room.settings.allowNewMembers)return send(res,403,{ok:false,error:"O administrador desativou novas entradas nesta rede."});
  if(memberList(room).filter(m=>m.connected).length>=room.settings.maxUsers)return send(res,409,{ok:false,error:"A rede atingiu o limite de usuários."});
  const token=enter(room,username,"member");return send(res,200,{ok:true,message:"Você entrou na rede.",network:{name:room.name,settings:room.settings,members:memberList(room)},token});
 }
 if(req.method==="GET"&&url.pathname==="/api/networks/me"){
  const g=getRoom(req);if(g.error)return send(res,...g.error);return send(res,200,{ok:true,network:{name:g.room.name,settings:g.room.settings,members:g.room.settings.showMembers?memberList(g.room):[],connectedCount:memberList(g.room).filter(m=>m.connected).length,role:g.s.role}});
 }
 if(req.method==="GET"&&url.pathname==="/api/networks/members"){
  const g=getRoom(req);if(g.error)return send(res,...g.error);return send(res,200,{ok:true,members:g.room.settings.showMembers?memberList(g.room):[],connectedCount:memberList(g.room).filter(m=>m.connected).length,showMembers:g.room.settings.showMembers});
 }
 if(req.method==="PUT"&&url.pathname==="/api/networks/settings"){
  const g=getRoom(req);if(g.error)return send(res,...g.error);if(g.s.role!=="owner")return send(res,403,{ok:false,error:"Somente o criador pode alterar as configurações."});
  let b;try{b=await json(req)}catch{return send(res,400,{ok:false,error:"JSON inválido."})}
  const s=g.room.settings;
  if(b.description!==undefined){const v=norm(b.description);if(v.length>120)return send(res,400,{ok:false,error:"Descrição limitada a 120 caracteres."});s.description=v}
  if(b.visibility!==undefined){if(!["private","listed"].includes(b.visibility))return send(res,400,{ok:false,error:"Visibilidade inválida."});s.visibility=b.visibility}
  if(b.maxUsers!==undefined){const n=Number(b.maxUsers);if(!Number.isInteger(n)||n<2||n>100)return send(res,400,{ok:false,error:"Limite de usuários deve ser de 2 a 100."});s.maxUsers=n}
  if(b.allowNewMembers!==undefined)s.allowNewMembers=Boolean(b.allowNewMembers);
  if(b.showMembers!==undefined)s.showMembers=Boolean(b.showMembers);
  if(b.gameVersion!==undefined){const v=norm(b.gameVersion);if(v.length>40)return send(res,400,{ok:false,error:"Versão muito longa."});s.gameVersion=v}
  if(b.serverAddress!==undefined){const v=norm(b.serverAddress);if(v.length>253||/[^a-zA-Z0-9.:[\]-]/.test(v))return send(res,400,{ok:false,error:"Endereço de servidor inválido."});s.serverAddress=v}
  if(b.serverPort!==undefined){const n=Number(b.serverPort);if(!Number.isInteger(n)||n<1||n>65535)return send(res,400,{ok:false,error:"Porta deve estar entre 1 e 65535."});s.serverPort=n}
  return send(res,200,{ok:true,message:"Configurações salvas.",network:{name:g.room.name,settings:s}});
 }
 if(req.method==="POST"&&url.pathname==="/api/networks/leave"){
  const g=getRoom(req);if(g.error)return send(res,...g.error);sessions.delete(g.s.tokenHash);g.room.members.delete(g.s.tokenHash);return send(res,200,{ok:true,message:"Você saiu da rede."});
 }
 if(req.method!=="GET"&&req.method!=="HEAD")return send(res,405,{ok:false,error:"Método não permitido."});
 let p;try{p=decodeURIComponent(url.pathname)}catch{return send(res,400,{ok:false,error:"Caminho inválido."})}
 if(p==="/")p="/index.html";const safe=normalize(p).replace(/^([/\\]|\.\.(?:[/\\]|$))+/, ""),file=join(root,safe);
 if(!file.startsWith(root))return send(res,403,{ok:false,error:"Acesso negado."});
 try{return send(res,200,(await readFile(file)).toString("utf8"),mime[extname(file)]||"application/octet-stream")}catch{return send(res,404,{ok:false,error:"Não encontrado."})}
});

const wss = new WebSocketServer({ noServer: true, maxPayload: 65536, perMessageDeflate: false });
server.on("upgrade", (req, socket, head) => {
  let url;
  try { url = new URL(req.url || "/", "http://localhost"); } catch { socket.destroy(); return; }
  if (url.pathname !== "/tunnel") { socket.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n"); socket.destroy(); return; }
  const auth = session(req);
  const room = auth && rooms.get(auth.roomKey);
  const member = room && room.members.get(auth.tokenHash);
  if (!auth || !room || !member) { socket.write("HTTP/1.1 401 Unauthorized\r\nConnection: close\r\n\r\n"); socket.destroy(); return; }
  wss.handleUpgrade(req, socket, head, (ws) => {
    if (member.tunnel && member.tunnel.readyState === WebSocket.OPEN) member.tunnel.close(4001, "Sessão substituída");
    member.tunnel = ws;
    member.lastSeen = Date.now();
    ws.on("message", (data, isBinary) => {
      member.lastSeen = Date.now();
      if (!isBinary || data.length > 65536) { ws.close(1003, "Somente pacotes binários são aceitos"); return; }
      for (const peer of room.members.values()) {
        if (peer === member || !peer.tunnel || peer.tunnel.readyState !== WebSocket.OPEN) continue;
        if (peer.tunnel.bufferedAmount > 2 * 1024 * 1024) { peer.tunnel.close(1013, "Cliente lento"); continue; }
        peer.tunnel.send(data, { binary: true });
      }
    });
    ws.on("close", () => { if (member.tunnel === ws) member.tunnel = null; member.lastSeen = Date.now(); });
    ws.on("error", () => { if (member.tunnel === ws) member.tunnel = null; });
  });
});

server.listen(port,"0.0.0.0",()=>console.log(`Minecraft Proxy VPN API and WebSocket relay listening on 0.0.0.0:${port}`));
