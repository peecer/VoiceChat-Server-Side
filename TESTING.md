# Validation — October 27, 2026

- Both add-ons compiled against their actual Simple Voice Chat 2.6.26 target API.
- Loopback transport integration tests passed against both target voice JARs: TCP-only bind (UDP remains available), fragmented frames, exact bytes, timestamp units, two-client routing/isolation, oversized input rejection, invalid-peer isolation, close/unblock, reconnect.
- Dependency SHA-512, size, mod ID/version and JAR ZIP integrity checked.
- Installer repeatability, existing configuration preservation and conflicting mod rejection tested locally.
- Minecraft 26.3 / Fabric Loader 0.20.1 / Java 25 server started with Fabric API, ViaFabric, ViaVersion, ViaBackwards, Simple Voice Chat and the custom TCP server add-on. Log evidence:

```
Loading Minecraft 26.3 with Fabric Loader 0.20.1
ViaVersion detected server version: 26.3 (777)
[voicechat] Registering events for 'peecer_voice_tcp_server'
[voicechat] Successfully registered ViaVersion mappings
Done (2.333s)! For help, type "help"
[voicechat] Using custom voicechat socket implementation: peecer.voice.tcp.TcpTransport$Server
[Peecer Voice TCP] Listening on TCP 24454; UDP is disabled.
[voicechat] Voice chat server started at port 24454
```

Server stopped normally with exit code 0. This environment could not reach Minecraft authentication discovery; no player login was tested. No graphical Minecraft client or microphones were available. Group voice, long-distance/dimension audio, real-world NAT/firewall traversal and audio quality need an actual two-player test. The socket isolation test is a transport routing test, not a live test of voice group permissions.
