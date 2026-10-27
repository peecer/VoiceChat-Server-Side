package peecer.voice.tcp;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.*;
import de.maxhenkel.voicechat.api.RawUdpPacket;

public final class TransportTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        TcpTransport.Server server = new TcpTransport.Server();
        TcpTransport.Client one = new TcpTransport.Client(), two = new TcpTransport.Client();
        ExecutorService tasks = Executors.newCachedThreadPool();
        try {
            server.open(0, "127.0.0.1");
            InetSocketAddress destination = new InetSocketAddress("127.0.0.1", server.getLocalPort());
            // TCP and UDP may bind the same numerical port independently. Successful UDP
            // bind here confirms the custom voice listener has not bound that UDP port.
            try (DatagramSocket unusedUdp = new DatagramSocket(destination)) {
                check(unusedUdp.getLocalPort() == server.getLocalPort(), "UDP must remain unused");
            }
            one.open(); two.open();
            Future<RawUdpPacket> beforeConnect = tasks.submit(one::read);
            one.send(new byte[]{1,2,3}, destination);
            RawUdpPacket first = tasks.submit(server::read).get(2, TimeUnit.SECONDS);
            check(Arrays.equals(first.getData(), new byte[]{1,2,3}), "client to server bytes");
            check(Math.abs(System.currentTimeMillis() - first.getTimestamp()) < 2000, "timestamp units");
            server.send(new byte[]{9,8}, first.getSocketAddress());
            check(Arrays.equals(beforeConnect.get(2, TimeUnit.SECONDS).getData(), new byte[]{9,8}), "read before connect");
            two.send(new byte[]{4}, destination);
            RawUdpPacket second = tasks.submit(server::read).get(2, TimeUnit.SECONDS);
            check(!first.getSocketAddress().equals(second.getSocketAddress()), "distinct peers");
            server.send(new byte[]{7}, second.getSocketAddress());
            check(tasks.submit(two::read).get(2, TimeUnit.SECONDS).getData()[0] == 7, "targeted return routing");
            Future<RawUdpPacket> isolated = tasks.submit(one::read);
            try { isolated.get(100, TimeUnit.MILLISECONDS); throw new AssertionError("cross-client leak"); }
            catch (TimeoutException expected) {}
            // Fragmented TCP input reconstructs exactly one bounded frame.
            try (Socket raw = new Socket("127.0.0.1", server.getLocalPort())) {
                DataInputStream in = new DataInputStream(raw.getInputStream());
                check(in.readInt() == TcpTransport.MAGIC, "server greeting");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeInt(TcpTransport.MAGIC); out.writeInt(3); out.write(new byte[]{11,12,13});
                for (byte b : bytes.toByteArray()) { raw.getOutputStream().write(b); raw.getOutputStream().flush(); }
                RawUdpPacket p = tasks.submit(server::read).get(2, TimeUnit.SECONDS);
                check(Arrays.equals(p.getData(), new byte[]{11,12,13}), "fragmented TCP reconstruction");
            }
            try (Socket bad = new Socket("127.0.0.1", server.getLocalPort())) {
                bad.setSoTimeout(2000);
                DataInputStream in = new DataInputStream(bad.getInputStream()); in.readInt();
                DataOutputStream out = new DataOutputStream(bad.getOutputStream());
                out.writeInt(TcpTransport.MAGIC); out.writeInt(Integer.MAX_VALUE); out.flush();
                check(in.read() == -1, "oversize packet closes peer before allocation");
            }
            try { one.send(new byte[TcpTransport.MAX_FRAME+1], destination); throw new AssertionError("oversize output accepted"); }
            catch (IOException expected) {}
            // Valid peers still function after malicious input.
            two.send(new byte[]{42}, destination);
            check(tasks.submit(server::read).get(2, TimeUnit.SECONDS).getData()[0] == 42, "bad peer isolation");
            one.close();
            try { isolated.get(2, TimeUnit.SECONDS); throw new AssertionError("closed read succeeded"); }
            catch (ExecutionException expected) { check(expected.getCause() instanceof IOException, "read close exception"); }
            Future<RawUdpPacket> blockedServer = tasks.submit(server::read);
            server.close();
            try { blockedServer.get(2, TimeUnit.SECONDS); throw new AssertionError("server read not closed"); }
            catch (ExecutionException expected) {}
            check(server.isClosed(), "server closed state");
            // Reopen both endpoints and establish a fresh session.
            server.open(0, "127.0.0.1"); one.open();
            one.send(new byte[]{55}, new InetSocketAddress("127.0.0.1", server.getLocalPort()));
            check(tasks.submit(server::read).get(2, TimeUnit.SECONDS).getData()[0] == 55, "reconnect");
            System.out.println("PASS: TCP-only bind, framing, two-client isolation, timestamps, size rejection, close and reconnect");
        } finally { one.close(); two.close(); server.close(); tasks.shutdownNow(); }
    }
}
