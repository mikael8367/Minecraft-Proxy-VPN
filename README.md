# Minecraft Proxy VPN

Painel experimental para redes nomeadas com senha e configurações básicas.

## Versão 0.3.0

- Criar e entrar em rede com senha.
- Definir nome de usuário.
- Configurações da rede: descrição, visibilidade informativa, limite de usuários, versão Minecraft, endereço/porta de referência, permitir novas entradas e mostrar/ocultar lista.
- Ver participantes e seus estados de sessão.
- Somente o criador da rede pode alterar configurações.
- Senhas armazenadas com scrypt e salt; sessões com tokens aleatórios.

## Limitações

Estas redes ainda são salas de autenticação, **não são túneis VPN** e não transportam tráfego Minecraft. A presença é aproximada por atividade recente na API. Salas e sessões ficam em memória e são apagadas em reinícios/deploys do Render. A opção de rede "listada" é apenas informativa, não cria diretório público.

## API

- `GET /api/health`, `GET /api/status`
- `POST /api/networks/create`, `POST /api/networks/join`
- `GET /api/networks/me`, `GET /api/networks/members`
- `PUT /api/networks/settings` (somente criador)
- `POST /api/networks/leave`

Requer Node.js 20+. No Render, use `npm install` e `npm start`. Para persistência, adicione banco de dados antes de uso real. Não armazene segredos no GitHub nem disponibilize proxy aberto.