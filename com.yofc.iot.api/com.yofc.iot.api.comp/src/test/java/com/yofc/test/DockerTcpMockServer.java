package com.yofc.test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class DockerTcpMockServer implements AutoCloseable {

    private static final int CONTAINER_PORT = 8000;

    private final String containerName;
    private final int mappedPort;

    private DockerTcpMockServer(String containerName, int mappedPort) {
        this.containerName = containerName;
        this.mappedPort = mappedPort;
    }

    static DockerTcpMockServer start() throws IOException, InterruptedException {
        assumeTrue(isDockerAvailable(), "Docker is not available; skipping Docker-backed TCP test");

        String name = "yofc-netty-test-" + UUID.randomUUID();
        Path testClasses = Path.of(System.getProperty("user.dir"), "target", "test-classes");
        runDocker(List.of(
                "run", "--rm", "-d",
                "--name", name,
                "--entrypoint", "/usr/bin/java",
                "-p", "127.0.0.1::" + CONTAINER_PORT,
                "-v", testClasses + ":/test-classes:ro",
                "kura-alpine:latest",
                "-cp", "/test-classes",
                MockTcpServer.class.getName(),
                String.valueOf(CONTAINER_PORT)));

        try {
            int port = mappedPort(name);
            waitUntilReady(port, Duration.ofSeconds(10));
            return new DockerTcpMockServer(name, port);
        } catch (IOException | InterruptedException | RuntimeException e) {
            runDocker(List.of("rm", "-f", name));
            throw e;
        }
    }

    int getMappedPort() {
        return mappedPort;
    }

    @Override
    public void close() throws IOException, InterruptedException {
        runDocker(List.of("rm", "-f", containerName));
    }

    private static boolean isDockerAvailable() {
        try {
            runDocker(List.of("version", "--format", "{{.Server.Version}}"));
            runDocker(List.of("image", "inspect", "kura-alpine:latest"));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int mappedPort(String name) throws IOException, InterruptedException {
        String output = runDocker(List.of("port", name, CONTAINER_PORT + "/tcp")).trim();
        int separator = output.lastIndexOf(':');
        if (separator < 0 || separator == output.length() - 1) {
            throw new IOException("Unexpected docker port output: " + output);
        }
        return Integer.parseInt(output.substring(separator + 1));
    }

    private static void waitUntilReady(int port, Duration timeout) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        IOException last = null;
        while (System.nanoTime() < deadline) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(100);
            }
        }
        throw last == null ? new IOException("Timed out waiting for TCP mock server") : last;
    }

    private static String runDocker(List<String> args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(args);
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Docker command timed out: " + command);
        }
        if (process.exitValue() != 0) {
            throw new IOException("Docker command failed: " + command + System.lineSeparator() + output);
        }
        return output;
    }
}
