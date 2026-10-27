package peecer.voice.tcp;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartingEvent;
public final class ServerPlugin implements VoicechatPlugin {
    public String getPluginId() { return "peecer_voice_tcp_server"; }
    public void registerEvents(EventRegistration events) {
        events.registerEvent(VoicechatServerStartingEvent.class,
                event -> event.setSocketImplementation(new TcpTransport.Server()));
    }
}
