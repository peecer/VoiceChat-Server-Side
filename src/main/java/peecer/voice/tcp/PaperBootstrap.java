package peecer.voice.tcp;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper/Leaf entry point. Fabric clients keep their existing TCP add-on. */
public final class PaperBootstrap extends JavaPlugin {
    private ServerPlugin transport;
    @Override public void onEnable() {
        BukkitVoicechatService service = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) {
            getLogger().severe("Simple Voice Chat Bukkit service unavailable. Install voicechat-bukkit-2.6.26.jar and restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        transport = new ServerPlugin();
        service.registerPlugin(transport);
        getLogger().info("Registered TCP voice transport for Paper/Leaf. Both clients and server need the TCP addon.");
        getLogger().info("Voice port comes from plugins/voicechat/voicechat-server.properties; allocate that port as TCP.");
    }
    @Override public void onDisable() {
        if (transport != null) transport.close();
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("peecer.voicetcp.admin")) { sender.sendMessage("You do not have permission."); return true; }
        TcpTransport.Server socket = transport == null ? null : transport.getSocket();
        if (socket == null || socket.isClosed()) {
            sender.sendMessage("Voice TCP: NOT LISTENING. Check startup logs and Simple Voice Chat Bukkit installation.");
        } else {
            sender.sendMessage("Voice TCP: LISTENING on TCP " + socket.getLocalPort() + ". UDP is not used.");
            sender.sendMessage("This reports the local listener, not external reachability. Open/allocate that TCP port in your host panel.");
            sender.sendMessage("Players need Simple Voice Chat 2.6.26 and peecer-voice-tcp-client-1.0.0.jar.");
        }
        return true;
    }
}
