package peecer.voice.tcp;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartingEvent;
public final class ServerPlugin implements VoicechatPlugin {
    private volatile TcpTransport.Server socket;
    public String getPluginId() { return "peecer_voice_tcp_server"; }
    public void registerEvents(EventRegistration events) {
        events.registerEvent(VoicechatServerStartingEvent.class, event -> {
            close();
            socket = new TcpTransport.Server();
            event.setSocketImplementation(socket);
        });
    }
    public TcpTransport.Server getSocket() { return socket; }
    public void close() {
        TcpTransport.Server current = socket;
        if (current != null) current.close();
    }
}
