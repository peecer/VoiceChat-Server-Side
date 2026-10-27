[Download server setup ZIP](https://github.com/peecer/VoiceChat-Server-Side/raw/refs/heads/main/downloads/VoiceChat-Server-26.3-Setup.zip)

# Voice Chat — Server

Fabric server setup for easy proximity voice and private worldwide groups, paired with a Minecraft **1.21.11 / Fabric Loader 0.18.2** client. Includes an original **Peecer Voice TCP** transport add-on for **Simple Voice Chat by Max Henkel**. It replaces the voice socket with framed TCP while retaining upstream audio encoding, authentication and groups.

## Pinned server target

Newest stable Minecraft release checked October 27, 2026: **26.3**. The newer **26.4 snapshot 6** is a testing snapshot and is not this server target.

| Component | Version |
|---|---|
| Minecraft server | 26.3 |
| Fabric Loader | 0.20.1 |
| Java | 25+ |
| Peecer Voice TCP server add-on | 1.0.0 |
| Simple Voice Chat | 2.6.26, upstream beta |
| ViaFabric | 0.4.22+188-26.x |
| ViaVersion | 5.12.2 |
| ViaBackwards | 5.12.2 |
| Fabric API | 0.165.0+26.3 |

The separate ViaVersion JAR supplies 5.12.2 instead of ViaFabric's bundled 5.12.1 snapshot. ViaBackwards is essential: ViaVersion alone does not allow an older client to join a newer server.

## Install on a Fabric server

1. Install a **Minecraft 26.3 Fabric server with Loader 0.20.1** and Java 25 using [Fabric's server installer](https://fabricmc.net/use/server/). Complete Minecraft's normal server setup and EULA review. This project does not provision hosting or install the Minecraft runtime.
2. Stop the server. Download this repository using **Code → Download ZIP** and extract it.
3. With Python 3.9+ installed, run `install-windows.bat` or `sh install-unix.sh`. The installer downloads all five official dependencies, adds the included TCP server add-on, verifies SHA-512 hashes and JAR metadata, and writes all six JARs into `installed/mods`.
4. Copy the contents of `installed/mods` into the server's `mods` directory. Copy `installed/config/voicechat/voicechat-server.properties` into the matching server config directory if there is no existing config. For an existing config, keep your settings and set `enable_groups=true`.
5. Remove older copies of these mods and Plasmo Voice. Do not mix this Fabric setup with Paper plugins.
6. Allocate/open **TCP 24454** in your host panel/firewall/router, or change `port` in `config/voicechat/voicechat-server.properties` to a separate TCP port assigned by your host. **No UDP is used with this add-on.** The voice port MUST differ from the Minecraft TCP port. Do not set it to -1. The existing Minecraft connection does not carry the audio.
7. Start the server. Install the [paired client](https://github.com/peecer/VoiceChat-Client-Side) on each player's game.

Advanced: `python install.py --output "/path/to/server"` installs directly while the server is stopped. Existing configs are preserved. The installer refuses conflicting/old copies of the managed mods instead of silently deleting them.

GitHub **Actions → Verify and package → latest successful run → voicechat-package** contains `VoiceChat-Server-26.3-Setup.zip`, an installer bundle. It needs internet access when installing; it is not an offline server distribution. Build it locally with `python build_pack.py`.

## Group voice

Players press **V**, finish microphone setup, then open **Groups / Join or Create Group**. Create a named group, optionally add a password, and choose **Isolated** for private group-only voice. Friends join from the same menu. Group audio is independent of distance, including between dimensions on this server. Leave the group for proximity chat. Groups are not a bridge to other Minecraft servers and should not be assumed to survive restarts.

Default configuration enables groups and keeps ordinary proximity range at 48 blocks. Client recording is disabled by server setting, although this cannot prevent recording through external software.

## Connection troubleshooting

- Run `/voicechat test PLAYERNAME` as an operator.
- A crossed-out plug means voice connection failure. Verify both endpoints have their TCP add-on, the allocated TCP voice port is open, and container mapping/firewall/public address agree. Check the server log for `[Peecer Voice TCP] Listening on TCP ...; UDP is disabled.`
- `voice_host` can advertise a different public voice hostname/port if your host requires it. Leave it empty for ordinary direct connections.
- If the host supplies only one TCP port and it is occupied by Minecraft, this add-on cannot work without another TCP allocation or TCP tunnel. It avoids UDP; it does not multiplex voice over the Minecraft port. The included compatibility mods translate game versions, not network ports.
- For an outdated-client error, confirm ViaFabric, ViaVersion and ViaBackwards all loaded.

## Compatibility and validation limits

Both TCP add-ons compile against their actual target voice JAR APIs. Loopback tests passed for a TCP listener leaving UDP free, frame reconstruction, two-client routing, timestamps, invalid frame rejection, close/unblock and reconnect. The upstream documents cross-version voice support with ViaVersion-family tools. Both voice builds use 2.6.26. The chosen Via components list 26.3 support; the supplied dependency manifests and JAR metadata were inspected and all hashes/ZIP integrity verified. The server voice JAR requires Java 25 and Loader >=0.19.3; Loader 0.20.1 satisfies that requirement.

**A real Minecraft 26.3 server boot passed** with all six mods, the custom TCP socket selected, TCP 24454 opened and a clean exit (code 0). **A graphical client launch, 1.21.11 player login and two-player microphone call remain unverified.** This is not a claim of end-to-end runtime certification. New 26.3 content is represented through the compatibility bridge on the older client; it cannot recreate a native 26.3 client. Arbitrary mods with custom registries are outside this setup's scope.

Before using an important world: boot a test server, join with two 1.21.11 clients, test nearby speech, create an isolated password group, move far apart and between dimensions, verify outsiders cannot hear it, then reconnect and retest. Test incorrect passwords and the leave-group button too.

## Upstream projects

- [Simple Voice Chat](https://github.com/henkelmax/simple-voice-chat)
- [ViaFabric](https://github.com/ViaVersion/ViaFabric)
- [ViaVersion](https://github.com/ViaVersion/ViaVersion)
- [ViaBackwards](https://github.com/ViaVersion/ViaBackwards)
- [Fabric API](https://github.com/FabricMC/fabric)
- [Voice server setup](https://dev.modrepo.de/minecraft/voicechat/wiki/server_setup)
- [Cross-version compatibility](https://dev.modrepo.de/minecraft/voicechat/wiki/compatibility)

Official binaries are fetched from their published Modrinth URLs and keep their original licenses. No uploaded reference JAR has been relabeled as a custom mod. Dependency versions are pinned for reproducibility and must be deliberately rechecked when updating.

## TCP transport details

This is an experimental 1.0.0 add-on. TCP retransmission can cause voice stutter or delays during packet loss, unlike UDP. A separate TCP socket is used so audio does not block the Minecraft connection. There is no UDP fallback. Both sides must run the same TCP add-on wire protocol.

The transport uses a versioned greeting, 4-byte frame lengths, a 4095-byte maximum frame, bounded receive/send queues, and 15-second read timeouts. It preserves the original voice payload unchanged; upstream voice authentication and encryption still apply. The server caps connections at 128 total and 8 per source IP, with 200 incoming packets/second per connection. Large groups behind one NAT may hit the per-IP cap. This is resource limiting, not a complete DDoS defense. Other custom voice-socket replacement add-ons are incompatible.

Build from source with **JDK 25 and Python 3.9+**: `python build.py` downloads the pinned voice API JAR, compiles original Java sources to Java 21 bytecode, runs TCP integration tests, and writes the add-on under `addons/`. The client needs Java 21; the Minecraft 26.3 server needs Java 25. `python build_pack.py` creates the launcher/server installation package. GitHub Actions repeats the build and tests.
