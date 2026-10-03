package com.aetherianartificer.townstead.devlink;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchHttpTest {

    private static final String TOKEN = "secret-token";

    private BenchHttp http;
    private final List<BenchHttp.Request> seen = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        http = new BenchHttp(TOKEN, request -> {
            seen.add(request);
            if (request.path().equals("/events")) return BenchHttp.Response.sse();
            if (request.path().equals("/boom")) throw new IllegalStateException("kaput");
            return BenchHttp.Response.json(200, "{\"path\":\"" + request.path() + "\",\"q\":\"" + request.query().getOrDefault("q", "") + "\"}");
        });
    }

    @AfterEach
    void stop() {
        http.close();
    }

    private record Reply(int status, Map<String, String> headers, String body) {}

    private Reply exchange(String method, String target, String host, String... headers) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", http.port())) {
            StringBuilder request = new StringBuilder(method + " " + target + " HTTP/1.1\r\n");
            if (host != null) request.append("Host: ").append(host).append("\r\n");
            for (String header : headers) request.append(header).append("\r\n");
            request.append("\r\n");
            OutputStream out = socket.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String statusLine = in.readLine();
            int status = Integer.parseInt(statusLine.split(" ")[1]);
            Map<String, String> replyHeaders = new java.util.LinkedHashMap<>();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                replyHeaders.put(line.substring(0, colon).toLowerCase(), line.substring(colon + 1).trim());
            }
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[1024];
            int read;
            while ((read = in.read(buffer)) > 0) body.append(buffer, 0, read);
            return new Reply(status, replyHeaders, body.toString());
        }
    }

    private String host() {
        return "127.0.0.1:" + http.port();
    }

    @Test
    void listensOnIpv4LoopbackEvenWhenTheJvmPrefersIpv6() {
        // Blockbench connects to 127.0.0.1; a ::1 socket refuses it (seen with CurseForge launches).
        assertEquals("/127.0.0.1", http.boundAddress().substring(http.boundAddress().indexOf('/'), http.boundAddress().lastIndexOf(':')));
    }

    @Test
    void rejectsMissingAndWrongTokens() throws Exception {
        assertEquals(401, exchange("GET", "/status", host()).status());
        assertEquals(401, exchange("GET", "/status", host(), "Authorization: Bearer nope").status());
        assertTrue(seen.isEmpty(), "the handler never sees an unauthenticated request");
    }

    @Test
    void acceptsBearerHeaderOrTokenQuery() throws Exception {
        Reply header = exchange("GET", "/roots/townstead_roots:overworlder?q=a%20b", host(), "Authorization: Bearer " + TOKEN);
        assertEquals(200, header.status());
        assertTrue(header.body().contains("\"path\":\"/roots/townstead_roots:overworlder\""), header.body());
        assertTrue(header.body().contains("\"q\":\"a b\""), header.body());
        assertEquals(200, exchange("GET", "/status?token=" + TOKEN, host()).status());
    }

    @Test
    void refusesForeignHostsSoRebindingCannotReachIt() throws Exception {
        assertEquals(403, exchange("GET", "/status", "evil.example:" + http.port(), "Authorization: Bearer " + TOKEN).status());
        assertEquals(403, exchange("GET", "/status", null, "Authorization: Bearer " + TOKEN).status());
        assertEquals(200, exchange("GET", "/status", "localhost:" + http.port(), "Authorization: Bearer " + TOKEN).status());
    }

    @Test
    void answersCorsPreflightWithoutAToken() throws Exception {
        Reply reply = exchange("OPTIONS", "/status", host(), "Origin: null", "Access-Control-Request-Headers: authorization");
        assertEquals(204, reply.status());
        assertEquals("*", reply.headers().get("access-control-allow-origin"));
        assertTrue(reply.headers().get("access-control-allow-headers").contains("Authorization"));
    }

    @Test
    void turnsHandlerFailuresIntoJsonErrors() throws Exception {
        Reply reply = exchange("GET", "/boom", host(), "Authorization: Bearer " + TOKEN);
        assertEquals(500, reply.status());
        assertTrue(reply.body().contains("kaput"), reply.body());
    }

    @Test
    void streamsBroadcastEvents() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", http.port())) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(("GET /events?token=" + TOKEN + " HTTP/1.1\r\nHost: " + host() + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            assertTrue(in.readLine().contains("200"));
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                // headers
            }
            assertEquals(": connected", in.readLine());
            long deadline = System.currentTimeMillis() + 2000;
            while (http.streamCount() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
            assertEquals(1, http.streamCount());
            http.broadcast("reload", "{}");
            in.readLine(); // blank line closing the greeting
            assertEquals("event: reload", in.readLine());
            assertEquals("data: {}", in.readLine());
        }
    }
}
