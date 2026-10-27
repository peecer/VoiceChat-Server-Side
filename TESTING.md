# Paper/Leaf correction 1.0.1

The supplied log identifies Leaf 26.3 build 8. It rejected Fabric artifacts because they lack plugin.yml. The replacement contains plugin.yml, a JavaPlugin entry point, a hard dependency on voicechat, BukkitVoicechatService registration and /voicetcp diagnostics.

Passed: compilation against real Bukkit and Simple Voice Chat Bukkit APIs; TCP framing/routing/reconnect integration tests; four installer tests including rejecting Fabric JARs and validating the actual Paper plugin descriptor; complete four-plugin installation with SHA-512 checks.

A Leaf 26.3 build 13 startup was attempted, but its bootstrap could not resolve the Mojang download host in this environment. A successful Leaf runtime startup and real two-player microphone call are NOT claimed. The previous successful Fabric server boot does not validate this Paper entry point.

Client 1.0.0 and wire protocol PVT1 remain unchanged. The host must allocate TCP 25791; this cannot be established from the startup log alone. Run /voicetcp after installation to check the local listener.
