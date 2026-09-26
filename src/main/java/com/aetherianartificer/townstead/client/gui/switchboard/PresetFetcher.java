package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.switchboard.SwitchboardPreset;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Downloads a preset from an https link. The body is read as preset data, never run. */
final class PresetFetcher {
    private PresetFetcher() {}

    private static final int MAX_BYTES = 1 << 20;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    enum Failure { NOT_HTTPS, UNREACHABLE, NOT_A_PRESET }

    static final class FetchException extends RuntimeException {
        final Failure failure;

        FetchException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }
    }

    static boolean looksLikeLink(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("https://") || t.startsWith("http://");
    }

    static CompletableFuture<SwitchboardPreset> fetch(String link) {
        URI uri;
        try {
            uri = URI.create(link.trim());
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new FetchException(Failure.UNREACHABLE));
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return CompletableFuture.failedFuture(new FetchException(Failure.NOT_HTTPS));
        }
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json, text/plain").GET().build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            if (response.statusCode() / 100 != 2 || !"https".equalsIgnoreCase(response.uri().getScheme())) {
                throw new FetchException(Failure.UNREACHABLE);
            }
            String body = read(response.body());
            String fallback = uri.getPath() == null ? "" : uri.getPath().replaceFirst(".*/", "").replaceFirst("(?i)\\.json$", "");
            try {
                return SwitchboardPreset.parse(body, fallback.isEmpty() ? uri.getHost() : fallback);
            } catch (IllegalArgumentException e) {
                throw new FetchException(Failure.NOT_A_PRESET);
            }
        });
    }

    private static String read(InputStream in) {
        try (in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) throw new FetchException(Failure.NOT_A_PRESET);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FetchException(Failure.UNREACHABLE);
        }
    }
}
