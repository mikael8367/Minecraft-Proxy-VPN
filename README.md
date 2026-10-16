# Minecraft Proxy VPN

Projeto educacional para desenvolver pelo Android um painel e uma API para redes nomeadas com senha, relacionado ao uso com Minecraft Java Multiplayer.

## Versão 0.2.0

- Interface responsiva em português.
- Criar uma rede informando nome e senha.
- Entrar em uma rede existente exigindo o nome e a senha.
- Senhas armazenadas como hash scrypt com salt, nunca em texto puro.
- Sessões com token aleatório, limites básicos de tentativas por IP e endpoints de estado.
- Endpoints `GET /api/health`, `GET /api/status`, `POST /api/networks/create`, `POST /api/networks/join`, `GET /api/networks/me` e `POST /api/networks/leave`.

## Limitações importantes

**As redes nesta versão são salas lógicas para demonstrar criação e autenticação, não redes VPN/túneis reais.** A aplicação não encaminha pacotes nem conecta clientes Minecraft uns aos outros.

As salas e sessões ficam na memória do processo e são apagadas quando o serviço Render reinicia ou faz deploy. Para uso persistente, a próxima etapa é adicionar banco de dados (por exemplo PostgreSQL) e controles de sessão adequados.

## Executar localmente

Requer Node.js 20 ou superior.

```sh
npm start
```

Abra `http://localhost:10000`.

## Publicar no Render

O serviço usa `PORT`, escuta em `0.0.0.0` e inclui `render.yaml` para configuração inicial. Após o deploy, teste `/api/health`.

## Próximas etapas para uma VPN real

1. Criar cliente Android nativo usando `VpnService` e consentimento do sistema.
2. Implementar gateway de túnel compatível, autenticação de dispositivos e regras de rede.
3. Persistir redes/usuários em banco de dados e armazenar sessões com expiração.
4. Testar em servidores Minecraft aos quais você tenha autorização de acesso.

Não coloque senhas, tokens ou chaves privadas no repositório. Não disponibilize encaminhamento de tráfego como proxy aberto.