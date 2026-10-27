package peecer.voice.tcp;

import de.maxhenkel.voicechat.api.RawUdpPacket;
import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Framed TCP transport for unchanged, authenticated Simple Voice Chat datagrams. */
public final class TcpTransport {
    static final int MAGIC = 0x50565431; // PVT1: wire protocol version 1
    static final int MAX_FRAME = 4095;
    static final int MAX_PEERS = 128;
    static final int MAX_PER_IP = 8;
    static final Packet CLOSED = new Packet(new byte[0], null, 0);
    private TcpTransport() {}

    static Thread worker(String name, Runnable task) {
        Thread t = new Thread(task, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    public record Packet(byte[] data, SocketAddress address, long timestamp) implements RawUdpPacket {
        public byte[] getData() { return data; }
        public SocketAddress getSocketAddress() { return address; }
        public long getTimestamp() { return timestamp; }
    }

    static Packet take(BlockingQueue<Packet> queue) throws IOException {
        try {
            Packet p = queue.take();
            if (p == CLOSED) {
                queue.offer(CLOSED);
                throw new SocketException("Voice TCP transport closed");
            }
            return p;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted voice TCP receive", e);
        }
    }

    static void signalClose(BlockingQueue<Packet> queue) {
        queue.clear();
        queue.offer(CLOSED);
    }

    static final class Peer implements AutoCloseable {
        final Socket socket;
        final SocketAddress address;
        final BlockingQueue<byte[]> outgoing = new ArrayBlockingQueue<>(32);
        final BlockingQueue<Packet> incoming;
        final Runnable onClose;
        final int maxPacketsPerSecond;
        final AtomicBoolean closed = new AtomicBoolean();
        volatile Thread reader, writer;

        Peer(Socket socket, BlockingQueue<Packet> incoming, Runnable onClose, int maxPacketsPerSecond) {
            this.socket = socket;
            this.address = socket.getRemoteSocketAddress();
            this.incoming = incoming;
            this.onClose = onClose;
            this.maxPacketsPerSecond = maxPacketsPerSecond;
        }

        void start() throws IOException {
            if (closed.get()) throw new SocketException("Voice TCP peer already closed");
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(15000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            writer = worker("voice-tcp-send", () -> {
                try {
                    out.writeInt(MAGIC);
                    out.flush();
                    while (!closed.get()) {
                        byte[] data = outgoing.take();
                        out.writeInt(data.length);
                        out.write(data);
                        out.flush();
                    }
                } catch (IOException | InterruptedException e) {
                    // Closing the peer also closes the socket and unblocks its reader.
                } finally { close(); }
            });
            reader = worker("voice-tcp-receive", () -> {
                try {
                    if (in.readInt() != MAGIC) throw new IOException("Wrong voice TCP protocol");
                    long window = System.nanoTime();
                    int count = 0;
                    while (!closed.get()) {
                        int length = in.readInt();
                        if (length < 1 || length > MAX_FRAME) throw new IOException("Invalid voice TCP frame size");
                        long now = System.nanoTime();
                        if (now - window >= TimeUnit.SECONDS.toNanos(1)) { window = now; count = 0; }
                        if (++count > maxPacketsPerSecond) throw new IOException("Voice TCP rate limit exceeded");
                        byte[] data = new byte[length];
                        in.readFully(data);
                        if (!incoming.offer(new Packet(data, address, System.currentTimeMillis()))) {
                            throw new IOException("Voice TCP receive queue full");
                        }
                    }
                } catch (IOException e) {
                    // Malformed, idle or disconnected peers are removed; no UDP fallback.
                } finally { close(); }
            });
        }

        void send(byte[] data) throws IOException {
            if (data.length < 1 || data.length > MAX_FRAME) throw new IOException("Invalid outgoing voice frame size");
            if (closed.get()) throw new SocketException("Voice TCP peer closed");
            if (!outgoing.offer(Arrays.copyOf(data, data.length))) {
                close();
                throw new IOException("Voice TCP send queue full; connection closed to limit latency");
            }
        }

        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            try { socket.close(); } catch (IOException ignored) {}
            Thread r = reader, w = writer;
            if (r != null) r.interrupt();
            if (w != null) w.interrupt();
            outgoing.clear();
            onClose.run();
        }
    }

    public static final class Server implements de.maxhenkel.voicechat.api.VoicechatSocket {
        private final Map<SocketAddress, Peer> peers = new ConcurrentHashMap<>();
        private final BlockingQueue<Packet> incoming = new ArrayBlockingQueue<>(1024);
        private volatile ServerSocket listener;
        private volatile boolean closed = true;

        public synchronized void open(int port, String bindAddress) throws IOException {
            close();
            incoming.clear();
            ServerSocket s = new ServerSocket();
            try {
                s.setReuseAddress(true);
                s.bind(bindAddress == null || bindAddress.isBlank()
                        ? new InetSocketAddress(port) : new InetSocketAddress(bindAddress, port), MAX_PEERS);
            } catch (IOException | RuntimeException e) { s.close(); throw e; }
            listener = s;
            closed = false;
            worker("voice-tcp-accept", () -> acceptLoop(s));
            System.out.println("[Peecer Voice TCP] Listening on TCP " + s.getLocalPort() + "; UDP is disabled.");
        }

        private void acceptLoop(ServerSocket s) {
            try {
                while (!closed && !s.isClosed()) {
                    Socket socket = s.accept();
                    long sameIp = peers.values().stream().filter(p -> p.socket.getInetAddress().equals(socket.getInetAddress())).count();
                    if (closed || peers.size() >= MAX_PEERS || sameIp >= MAX_PER_IP) { socket.close(); continue; }
                    SocketAddress address = socket.getRemoteSocketAddress();
                    Peer peer = new Peer(socket, incoming, () -> peers.remove(address), 200);
                    peers.put(address, peer);
                    try { peer.start(); } catch (IOException e) { peer.close(); }
                }
            } catch (IOException e) {
                if (!closed && listener == s) {
                    System.err.println("[Peecer Voice TCP] Listener failed: " + e.getMessage());
                    close();
                }
            }
        }

        public RawUdpPacket read() throws IOException { return take(incoming); }
        public void send(byte[] data, SocketAddress address) throws IOException {
            Peer peer = peers.get(address);
            if (peer != null) peer.send(data);
            // Late packets after a disconnect are dropped like normal datagrams.
        }
        public int getLocalPort() { ServerSocket s = listener; return s == null ? -1 : s.getLocalPort(); }
        public boolean isClosed() { return closed; }
        public synchronized void close() {
            closed = true;
            ServerSocket s = listener;
            listener = null;
            if (s != null) try { s.close(); } catch (IOException ignored) {}
            for (Peer peer : peers.values()) peer.close();
            peers.clear();
            signalClose(incoming);
        }
    }

    public static final class Client implements de.maxhenkel.voicechat.api.ClientVoicechatSocket {
        private final BlockingQueue<Packet> incoming = new ArrayBlockingQueue<>(256);
        private volatile Peer peer;
        private volatile Socket connecting;
        private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
        private volatile boolean closed = true;
        public synchronized void open() {
            close();
            incoming.clear();
            closed = false;
        }
        public RawUdpPacket read() throws IOException { return take(incoming); }
        public synchronized void send(byte[] data, SocketAddress address) throws IOException {
            if (closed) throw new SocketException("Voice TCP client closed");
            Peer p = peer;
            if (p == null) {
                long activeGeneration = generation.get();
                Socket socket = new Socket();
                connecting = socket;
                try {
                    socket.connect(address, 5000);
                    if (closed) throw new SocketException("Voice TCP client closed while connecting");
                    p = new Peer(socket, incoming, () -> {
                        if (generation.get() == activeGeneration) { closed = true; signalClose(incoming); }
                    }, 8000);
                    peer = p;
                    if (closed || generation.get() != activeGeneration) {
                        p.close();
                        throw new SocketException("Voice TCP session closed while connecting");
                    }
                    p.start();
                } catch (IOException | RuntimeException e) {
                    try { socket.close(); } catch (IOException ignored) {}
                    closed = true;
                    signalClose(incoming);
                    throw e;
                } finally { connecting = null; }
            }
            if (!p.address.equals(address)) throw new IOException("Voice server address changed; reconnect required");
            p.send(data);
        }
        public boolean isClosed() { return closed; }
        public void close() {
            generation.incrementAndGet();
            closed = true;
            Socket c = connecting;
            if (c != null) try { c.close(); } catch (IOException ignored) {}
            Peer p = peer;
            peer = null;
            if (p != null) p.close();
            signalClose(incoming);
        }
    }
}
