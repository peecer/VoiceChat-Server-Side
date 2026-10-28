# Peecer Voice TCP — Leaf / Paper server

**Fixed for the server in the supplied log: Leaf 26.3 (Paper/Bukkit), not Fabric.** The old Fabric server JARs cannot be placed in Leaf's `plugins` folder. Version **1.0.1** supplies the real Bukkit entry point and `plugin.yml`, and registers the same TCP voice transport through Simple Voice Chat's Bukkit API. Keep the existing Fabric 1.21.11 / Loader 0.18.2 client and TCP client add-on 1.0.0.

## Direct downloads

- [Peecer Voice TCP Paper 1.0.1](https://github.com/peecer/VoiceChat-Server-Side/raw/refs/heads/main/addons/peecer-voice-tcp-paper-1.0.1.jar)
- [Simple Voice Chat Bukkit 2.6.26](https://cdn.modrinth.com/data/9eGKb6K1/versions/CxMI5WBR/voicechat-bukkit-2.6.26.jar)
- [ViaVersion 5.12.2 — Paper/Bukkit](https://cdn.modrinth.com/data/P1OZGk5p/versions/Fkfcr89K/ViaVersion-5.12.2.jar)
- [ViaBackwards 5.12.2 — Paper/Bukkit](https://cdn.modrinth.com/data/NpvuJQoq/versions/WFO37e8T/ViaBackwards-5.12.2.jar)
- [Installer ZIP](https://github.com/peecer/VoiceChat-Server-Side/raw/refs/heads/main/downloads/VoiceChat-Leaf-Paper-26.3-1.0.1-Setup.zip)

The log already shows ViaVersion and ViaBackwards 5.12.2 loaded successfully. Keep those existing copies; do not add duplicates. Simple Voice Chat 2.6.26 is an upstream beta.

## Fix the existing server

1. **Stop the server.** Keep Leaf/Paper and your other working plugins.
2. Remove these incorrect Fabric JARs from `plugins` (including duplicate names ending in `(1)`):
   - `peecer-voice-tcp-server-1.0.0.jar`
   - `voicechat-fabric-2.6.26+26.3.jar`
   - `ViaFabric-0.4.22+188-26.x.jar`
   - `fabric-api-0.165.0+26.3.jar`, if present.
3. Put **peecer-voice-tcp-paper-1.0.1.jar** and **voicechat-bukkit-2.6.26.jar** into `plugins`.
4. Keep the already-working Bukkit versions of ViaVersion/ViaBackwards. These let a 1.21.11 client join the 26.3 server.
5. Set these values in **plugins/voicechat/voicechat-server.properties** (create the folder/file if absent):

```properties
port=25791
voice_host=25791
enable_groups=true
```

The host startup log explicitly assigns **25791**. Confirm the host makes that port reachable as **TCP**, not only UDP. Port 25791 is separate from the game TCP port 25565. The add-on does not share the Minecraft connection and cannot create host port allocations. If your host assigns a different port, use that allocation consistently. No UDP is used by this add-on.

6. Restart fully; do not use `/reload`. Run **`/voicetcp`** as an operator. Expected: `Voice TCP: LISTENING on TCP 25791. UDP is not used.` This checks the local listener, not the firewall/public connection.
7. On each player's client, keep [Simple Voice Chat for 1.21.11](https://cdn.modrinth.com/data/9eGKb6K1/versions/hBnJeESK/voicechat-fabric-1.21.11-2.6.26.jar) and [Peecer Voice TCP Client 1.0.0](https://github.com/peecer/VoiceChat-Client-Side/raw/refs/heads/main/addons/peecer-voice-tcp-client-1.0.0.jar) in the client `mods` folder. The server correction does not require a new client JAR.

## Crossed-out plug / connection refused

Check the client log after joining. `Using custom voicechat client socket implementation: peecer.voice.tcp.TcpTransport$Client` confirms the TCP add-on loaded. A later `Connecting to voice chat server` line shows the actual endpoint being attempted.

The client endpoint must use the allocated voice TCP port **25791**, matching `port` in the active server config and the `/voicetcp` listener. This template sets `voice_host=25791` explicitly, keeping the game connection's server IP. If the public voice endpoint uses a different IP, set `voice_host=PUBLIC_SERVER_IP:25791` instead. Do not point it at the Minecraft game port.

After changing `plugins/voicechat/voicechat-server.properties`, fully stop and start the server, then reconnect the player. Existing installations preserve their config; downloading a new installer does not overwrite it. `Connection refused` means the attempted TCP connection was rejected; confirm the advertised endpoint, TCP port allocation and forwarding/firewall rules. A local `LISTENING` message alone does not confirm public reachability. On Windows, test `Test-NetConnection PUBLIC_SERVER_IP -Port 25791` from the client computer. No UDP port is needed by this transport.

## Groups

Press **V**, complete microphone setup, open **Groups**, create a named/password group and choose **Isolated**. Members can talk at any distance and across dimensions on this server. Leave the group to return to proximity chat. The existing Simple Voice Chat engine supplies group management and permissions; it is not reimplemented by this transport add-on.

## Optional installer

With Python 3.9+, extract the installer ZIP and run `install-windows.bat` or `sh install-unix.sh`. Copy the generated `installed/plugins` contents into your stopped server. Existing voice configuration is preserved by direct installation with `python install.py --output /path/to/server`. Conflicting Fabric mods and old/renamed duplicate plugins are rejected rather than silently deleted. Existing unrelated plugins are left alone.

The installer contains the custom JAR and downloads the three official Bukkit dependencies with SHA-512 verification. It does not include Leaf itself or change the server runtime. Java 25+ is required by Minecraft 26.3; the user's Java 27 is not the cause of the logged missing-plugin-descriptor errors.

## Build and checks

Use JDK 25 and Python 3.9+: `python build.py`, then `python build_pack.py`. The compiler uses actual upstream Simple Voice Chat and Bukkit API JARs. The build-only Spigot API is not a plugin to install; Leaf already supplies its Bukkit/Paper API. GitHub Actions compiles, tests, validates dependencies and packages the result.

See [TESTING.md](TESTING.md) for exact tests and limitations. TCP retransmission can delay voice under packet loss. Both endpoints need the TCP add-on; ordinary UDP-only voice clients cannot connect to this TCP listener. The transport bounds frames/queues and connections, and preserves upstream authenticated/encrypted payloads. Resource limits are 128 connections, 8 per source IP and 200 inbound packets/second per connection. These are not a complete DDoS defense.

The log also contains an unrelated GrimAC warning about older clients using vehicles with ViaBackwards. This voice fix does not change your anti-cheat settings or resolve that upstream compatibility limitation.
