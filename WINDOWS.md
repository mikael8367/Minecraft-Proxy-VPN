# Mikael VPN para Windows

Cliente de desktop para Windows 10/11 x64. Esta primeira versão permite criar uma sala ou entrar em uma sala existente no serviço do projeto.

## Compilar
No Windows com .NET 8 SDK:
`dotnet publish windows/MikaelVPN.csproj -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o publish`

## Limitação importante
Este cliente ainda **não cria um adaptador VPN de rede no Windows**. O servidor atual retransmite mensagens WebSocket e não implementa sozinho uma VPN IP completa. Portanto, criar/entrar em uma sala não significa que Minecraft Java/Bedrock já consiga descobrir ou conectar à LAN. Para isso será necessário integrar um adaptador como Wintun, implementar roteamento de pacotes e testar entre dois PCs/PC e celular.