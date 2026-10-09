package com.yofc.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MockTcpServer implements AutoCloseable {

    private final ServerSocket listener;
    private final Thread acceptor;

    private static final byte[] SMPINF = "SMPINF?".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DAT = "DAT?".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SMPINF_RESPONSE = "SMPINF:OK".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DAT_RESPONSE = "0123456789".getBytes(StandardCharsets.UTF_8);

    private MockTcpServer(int port) throws IOException {
        listener = new ServerSocket(port, 10, port == 0 ? java.net.InetAddress.getLoopbackAddress() : null);
        acceptor = new Thread(() -> {
            while (!listener.isClosed()) {
                try {
                    Socket socket = listener.accept();
                    socket.setSoTimeout(5000);
                    Thread client = new Thread(() -> handle(socket), "mock-tcp-client");
                    client.setDaemon(true);
                    client.start();
                } catch (IOException e) {
                    if (!listener.isClosed()) throw new java.io.UncheckedIOException(e);
                }
            }
        }, "mock-tcp-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    static MockTcpServer start() throws IOException {
        return new MockTcpServer(0);
    }

    int getMappedPort() {
        return listener.getLocalPort();
    }

    @Override
    public void close() throws IOException, InterruptedException {
        listener.close();
        acceptor.join(5000);
    }

    public static void main(String[] args) throws Exception {
        try (MockTcpServer server = new MockTcpServer(args.length == 0 ? 8000 : Integer.parseInt(args[0]))) {
            server.acceptor.join();
        }
    }

    private static void handle(Socket socket) {
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            byte[] buffer = new byte[64];
            int length;
            while ((length = in.read(buffer)) != -1) {
                byte[] message = Arrays.copyOf(buffer, length);
                if (startsWith(message, SMPINF)) {
                    out.write(SMPINF_RESPONSE);
                    out.flush();
                } else if (startsWith(message, DAT)) {
                    out.write(ByteBuffer.allocate(Integer.BYTES).putInt(DAT_RESPONSE.length).array());
                    out.flush();
                    out.write(DAT_RESPONSE);
                    out.flush();
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean startsWith(byte[] message, byte[] prefix) {
        if (message.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (message[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
