# Minecraft Proxy VPN

Projeto educacional para desenvolver pelo Android um painel e uma API de diagnóstico relacionados a Minecraft Java Multiplayer.

## Estado atual

**Versão 0.1.0 — painel e API de diagnóstico, não é uma VPN funcional.**

- Interface responsiva em português.
- Endpoint `GET /api/health` para health check.
- Endpoint `GET /api/status` para mostrar o estado da API.
- Campo de endereço/porta salvo apenas no navegador.
- Configuração inicial do Render em `render.yaml`.

Este projeto não encaminha pacotes, não cria um proxy TCP e não inicia um túnel VPN. O indicador de VPN permanece como não configurado até a implementação de um cliente Android baseado em `VpnService` e um gateway compatível.

## Executar localmente

Requer Node.js 20 ou superior.

```sh
npm start
```

Abra `http://localhost:10000`.

## Publicar no Render

1. No Render, escolha **New → Blueprint** e conecte este repositório, ou crie um Web Service Node.
2. Use `npm install` como Build Command e `npm start` como Start Command.
3. Após o deploy, abra `/api/health` e confirme o JSON com `"ok": true`.

O serviço escuta em `0.0.0.0` e usa a variável `PORT` do ambiente.

## Próximas etapas

1. Criar o cliente Android nativo e a tela de consentimento de VPN.
2. Definir protocolo e gateway de túnel com autenticação e chaves seguras.
3. Testar com um servidor de Minecraft que você tenha autorização para acessar.
4. Medir latência sem prometer redução de ping.

## Segurança

Não coloque senhas, tokens, chaves privadas ou credenciais no repositório. Não transforme o servidor em proxy aberto. Use autenticação, limites de uso e regras de rede antes de disponibilizar encaminhamento de tráfego.