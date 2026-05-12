package com.yofc.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class MockTcpServer {

    private static final byte[] SMPINF = "SMPINF?".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DAT = "DAT?".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SMPINF_RESPONSE = "SMPINF:OK".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DAT_RESPONSE = "0123456789".getBytes(StandardCharsets.UTF_8);

    private MockTcpServer() {
    }

    public static void main(String[] args) throws IOException {
        int port = args.length == 0 ? 8000 : Integer.parseInt(args[0]);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handle(socket), "mock-tcp-client");
                clientThread.setDaemon(true);
                clientThread.start();
            }
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
