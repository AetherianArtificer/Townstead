package com.aetherianartificer.townstead.devlink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal HTTP/1.1 + Server-Sent Events server for Bench Link, on a plain {@link ServerSocket}
 * so it runs on any Java runtime (Mojang's bundled one may not ship {@code jdk.httpserver}).
 *
 * <p>It binds the loopback address only, closes every plain response after one exchange, and
 * keeps SSE connections open for {@link #broadcast}. Every request must carry the session token,
 * as {@code Authorization: Bearer <token>} or, for {@code EventSource} which cannot set headers,
 * a {@code token} query parameter. The {@code Host} header must name the loopback address, which
 * stops a web page from reaching the server through DNS rebinding.</p>
 */
final class BenchHttp {

    /** One parsed request. {@code path} is decoded and has no query string. */
    record Request(String method, String path, Map<String, String> query, Map<String, String> headers, byte[] body) {
        String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** A complete response; {@link #sse()} marks the event-stream upgrade instead. */
    record Response(int status, String contentType, byte[] body, Map<String, String> headers) {
        static Response json(int status, String json) {
            return new Response(status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), Map.of());
        }

        static Response bytes(String contentType, byte[] body) {
            return new Response(200, contentType, body, Map.of());
        }

        static Response error(int status, String message) {
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            obj.addProperty("error", message);
            return json(status, obj.toString());
        }

        static Response sse() {
            return new Response(-1, "text/event-stream", new byte[0], Map.of());
        }

        boolean isSse() {
            return status == -1;
        }
    }

    interface Handler {
        Response handle(Request request) throws Exception;
    }

    // Its own logger, not Townstead.LOGGER: the HTTP layer must not pull in the mod class.
    private static final Logger LOGGER = LoggerFactory.getLogger("townstead/bench-link");

    private static final InetAddress LOOPBACK;

    static {
        try {
            LOOPBACK = InetAddress.getByAddress("localhost", new byte[]{127, 0, 0, 1});
        } catch (java.net.UnknownHostException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    private static final int SOCKET_TIMEOUT_MS = 10_000;

    private final String token;
    private final Handler handler;
    private final ServerSocket socket;
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService heartbeat;
    private final List<OutputStream> streams = new CopyOnWriteArrayList<>();
    private volatile boolean running = true;

    BenchHttp(String token, Handler handler) throws IOException {
        this.token = token;
        this.handler = handler;
        // Always IPv4 127.0.0.1: getLoopbackAddress() is ::1 when the launcher sets
        // java.net.preferIPv6Addresses, and Blockbench connects to 127.0.0.1.
        this.socket = new ServerSocket(0, 50, LOOPBACK);
        AtomicInteger count = new AtomicInteger();
        this.workers = new ThreadPoolExecutor(1, 16, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64), r -> {
            Thread thread = new Thread(r, "Townstead Bench Link worker " + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "Townstead Bench Link heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        // A comment line keeps idle streams from timing out in proxies and detects dead clients.
        heartbeat.scheduleAtFixedRate(() -> writeAll(": ping\n\n"), 15, 15, TimeUnit.SECONDS);
        Thread accept = new Thread(this::acceptLoop, "Townstead Bench Link");
        accept.setDaemon(true);
        accept.start();
    }

    int port() {
        return socket.getLocalPort();
    }

    /** The address actually bound, for the log (proves which loopback family it is on). */
    String boundAddress() {
        return String.valueOf(socket.getLocalSocketAddress());
    }

    /** Open event streams, one per connected Blockbench session. */
    int streamCount() {
        return streams.size();
    }

    void broadcast(String event, String json) {
        writeAll("event: " + event + "\ndata: " + json + "\n\n");
    }

    void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        for (OutputStream stream : streams) {
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
        streams.clear();
        heartbeat.shutdownNow();
        workers.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = socket.accept();
                try {
                    workers.execute(() -> serve(client));
                } catch (java.util.concurrent.RejectedExecutionException full) {
                    client.close();
                }
            } catch (SocketException closed) {
                return;
            } catch (IOException e) {
                LOGGER.warn("Bench Link accept failed", e);
            }
        }
    }

    private void serve(Socket client) {
        boolean keepOpen = false;
        try {
            client.setSoTimeout(SOCKET_TIMEOUT_MS);
            Request request = read(client.getInputStream());
            OutputStream out = new BufferedOutputStream(client.getOutputStream());
            if (request == null) {
                write(out, Response.error(400, "malformed request"));
                return;
            }
            if ("OPTIONS".equals(request.method())) {
                write(out, new Response(204, "text/plain", new byte[0], Map.of()));
                return;
            }
            if (!loopbackHost(request.header("host"))) {
                write(out, Response.error(403, "host not allowed"));
                return;
            }
            if (!authorized(request)) {
                write(out, Response.error(401, "missing or wrong token"));
                return;
            }
            Response response;
            try {
                response = handler.handle(request);
            } catch (Exception | LinkageError e) {
                // A dev tool should answer, not hang up: a class that failed to load is reported too.
                LOGGER.warn("Bench Link request {} {} failed", request.method(), request.path(), e);
                response = Response.error(500, e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            if (response.isSse()) {
                client.setSoTimeout(0);
                out.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Cache-Control: no-cache\r\n"
                        + "Connection: keep-alive\r\n"
                        + corsHeaders()
                        + "\r\n: connected\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                streams.add(out);
                keepOpen = true;
                BenchLink.onStreamsChanged();
                return;
            }
            write(out, response);
        } catch (IOException e) {
            // Client went away mid-exchange; nothing to answer.
        } finally {
            if (!keepOpen) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void writeAll(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        boolean dropped = false;
        for (OutputStream stream : streams) {
            try {
                synchronized (stream) {
                    stream.write(bytes);
                    stream.flush();
                }
            } catch (IOException gone) {
                streams.remove(stream);
                dropped = true;
                try {
                    stream.close();
                } catch (IOException ignored) {
                }
            }
        }
        if (dropped) BenchLink.onStreamsChanged();
    }

    private boolean authorized(Request request) {
        String supplied = null;
        String header = request.header("authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) supplied = header.substring(7).trim();
        if (supplied == null) supplied = request.query().get("token");
        return supplied != null && MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    private boolean loopbackHost(String host) {
        if (host == null) return false;
        String name = host;
        if (name.startsWith("[")) {
            int end = name.indexOf(']');
            name = end < 0 ? name : name.substring(1, end);
        } else {
            int colon = name.lastIndexOf(':');
            if (colon >= 0) name = name.substring(0, colon);
        }
        return name.equals("127.0.0.1") || name.equalsIgnoreCase("localhost") || name.equals("::1");
    }

    private static String corsHeaders() {
        return "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: Authorization, Content-Type\r\n"
                + "Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS\r\n"
                + "Access-Control-Expose-Headers: X-Bench-Pack\r\n";
    }

    private static void write(OutputStream out, Response response) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(response.status()).append(' ').append(reason(response.status())).append("\r\n");
        head.append("Content-Type: ").append(response.contentType()).append("\r\n");
        head.append("Content-Length: ").append(response.body().length).append("\r\n");
        head.append("Cache-Control: no-store\r\n");
        head.append("Connection: close\r\n");
        head.append(corsHeaders());
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            head.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        out.write(response.body());
        out.flush();
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 413 -> "Payload Too Large";
            case 501 -> "Not Implemented";
            case 503 -> "Service Unavailable";
            default -> status >= 500 ? "Internal Server Error" : "Status";
        };
    }

    /** Parses one request, or returns null when it is malformed or oversized. */
    private static Request read(InputStream in) throws IOException {
        ByteArrayOutputStream headBytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) return null;
            headBytes.write(b);
            if (headBytes.size() > MAX_HEADER_BYTES) return null;
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3))
                    ? matched + 1 : (b == '\r' ? 1 : 0);
        }
        String[] lines = headBytes.toString(StandardCharsets.ISO_8859_1).split("\r\n");
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 2) return null;
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        byte[] body = new byte[0];
        String length = headers.get("content-length");
        if (length != null) {
            int size;
            try {
                size = Integer.parseInt(length);
            } catch (NumberFormatException e) {
                return null;
            }
            if (size < 0 || size > MAX_BODY_BYTES) return null;
            body = in.readNBytes(size);
            if (body.length != size) return null;
        }
        String target = requestLine[1];
        int q = target.indexOf('?');
        String rawPath = q < 0 ? target : target.substring(0, q);
        Map<String, String> query = new LinkedHashMap<>();
        if (q >= 0) {
            for (String pair : target.substring(q + 1).split("&")) {
                if (pair.isEmpty()) continue;
                int eq = pair.indexOf('=');
                String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                query.put(key, value);
            }
        }
        // Decode %XX only; a '+' in a path is literal.
        String path = URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8);
        return new Request(requestLine[0].toUpperCase(Locale.ROOT), path, query, headers, body);
    }
}
