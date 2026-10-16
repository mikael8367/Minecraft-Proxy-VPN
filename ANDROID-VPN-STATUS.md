# Android VPN agent status

This repository now contains:
- Password-protected room coordination API.
- Authenticated WebSocket packet relay at `wss://<host>/tunnel`.
- Initial Android Kotlin app with a `VpnService` TUN interface and WebSocket packet transport.
- GitHub Actions workflow to build a debug APK.

## Important status

This is an **experimental first implementation**, not a verified production VPN. It has not yet been built or tested on two physical Android phones. The relay forwards binary packets to other authenticated participants in the same in-memory room. TLS protects transport between each client and the relay, but this version does not implement end-to-end packet encryption, multi-peer IP routing, NAT traversal, or reliable reconnection. Do not use it for sensitive traffic.

The room/session store is in memory and resets when the Render service restarts or deploys. Create the room again after a reset.

## Minecraft testing

The first target is one host and one friend:
- Host VPN IP: `10.77.0.1`
- Friend VPN IP: `10.77.0.2`
- Java server default port: `25565/TCP`
- Bedrock default port: `19132/UDP`

Minecraft LAN discovery is not forwarded as a discovery service. The game/server must listen on a reachable interface and the client must support entering the VPN IP and port. Android-hosted Bedrock worlds and FCL/Pojav Java worlds must be tested separately.

## Build

GitHub Actions builds a debug APK from the repository. Download it from the workflow run's Artifacts section. A successful build does not mean the network tunnel or Minecraft connectivity has passed runtime tests.
