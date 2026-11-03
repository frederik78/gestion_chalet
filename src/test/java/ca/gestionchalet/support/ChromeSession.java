package ca.gestionchalet.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Un Chrome sans interface, à profil jetable, piloté par son protocole de débogage avec le seul JDK : aucune dépendance de
 * navigateur n'entre dans le projet. Il exige un Chrome installé : voir {@code RecuScreensChromeTest}.
 */
public final class ChromeSession implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final Process process;
    private final WebSocket socket;
    private final AtomicInteger ids = new AtomicInteger();
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();

    public ChromeSession(String binary, Path profile) throws IOException, InterruptedException {
        process = new ProcessBuilder(binary, "--headless=new", "--disable-gpu", "--no-first-run", "--no-default-browser-check",
                "--remote-debugging-port=0", "--user-data-dir=" + profile, "about:blank")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        try {
            socket = connect(profile);
            call("Page.enable", Map.of());
            call("Network.enable", Map.of());
            call("Emulation.setDeviceMetricsOverride", Map.of("width", 1000, "height", 900, "deviceScaleFactor", 1, "mobile", false));
        } catch (IOException | InterruptedException | RuntimeException e) {
            close();
            throw e;
        }
    }

    private WebSocket connect(Path profile) throws IOException, InterruptedException {
        HttpClient http = HttpClient.newHttpClient();
        String port = readDebugPort(profile.resolve("DevToolsActivePort"));
        HttpResponse<String> targets = http.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).build(), HttpResponse.BodyHandlers.ofString());
        String url = null;
        for (JsonNode target : JSON.readTree(targets.body())) {
            if ("page".equals(target.get("type").asString())) {
                url = target.get("webSocketDebuggerUrl").asString();
            }
        }
        return http.newWebSocketBuilder().buildAsync(URI.create(url), new Listener()).join();
    }

    private static String readDebugPort(Path file) throws IOException, InterruptedException {
        Instant limit = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(limit)) {
            if (Files.exists(file) && Files.size(file) > 0) {
                return Files.readAllLines(file).getFirst();
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Chrome n'a pas ouvert son port de débogage");
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder message = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            message.append(data);
            if (last) {
                JsonNode node = JSON.readTree(message.toString());
                message.setLength(0);
                if (node.has("id")) {
                    pending.remove(node.get("id").asInt()).complete(node);
                }
            }
            webSocket.request(1);
            return null;
        }
    }

    private JsonNode call(String method, Map<String, Object> params) {
        int id = ids.incrementAndGet();
        CompletableFuture<JsonNode> response = new CompletableFuture<>();
        pending.put(id, response);
        socket.sendText(JSON.writeValueAsString(Map.of("id", id, "method", method, "params", params)), true).join();
        JsonNode node = response.orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
        if (node.has("error")) {
            throw new IllegalStateException(method + " : " + node.get("error"));
        }
        return node.get("result");
    }

    public void open(String url) {
        call("Page.navigate", Map.of("url", url));
    }

    /** Évalue une expression dans la page, promesse attendue ; le résultat est rendu en JSON. */
    public JsonNode eval(String expression) {
        JsonNode result = call("Runtime.evaluate", Map.of("expression", expression, "awaitPromise", true, "returnByValue", true));
        if (result.has("exceptionDetails")) {
            throw new IllegalStateException(expression + " : " + result.get("exceptionDetails"));
        }
        JsonNode value = result.get("result").get("value");
        return value == null ? JSON.nullNode() : value;
    }

    /** Attend qu'une expression soit vraie, sans jamais échouer sur un changement de page en cours. */
    public void waitUntil(String expression) throws InterruptedException {
        Instant limit = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(limit)) {
            try {
                if (eval(expression).asBoolean(false)) {
                    return;
                }
            } catch (IllegalStateException e) {
                // Le contexte de la page change pendant une navigation : on réessaie.
                continue;
            } finally {
                Thread.sleep(25);
            }
        }
        throw new AssertionError("Toujours faux après " + TIMEOUT + " : " + expression);
    }

    /** Choisit des fichiers comme le ferait l'utilisateur : le navigateur déclenche lui-même l'événement {@code change}. */
    public void chooseFiles(String selector, Path... files) {
        JsonNode document = call("DOM.getDocument", Map.of());
        JsonNode node = call("DOM.querySelector", Map.of("nodeId", document.get("root").get("nodeId").asInt(), "selector", selector));
        List<String> paths = new ArrayList<>();
        for (Path file : files) {
            paths.add(file.toAbsolutePath().toString());
        }
        call("DOM.setFileInputFiles", Map.of("nodeId", node.get("nodeId").asInt(), "files", paths));
    }

    /** Coupe ou rétablit le réseau de la page : un {@code fetch} y échoue comme lors d'une vraie panne. */
    public void setOffline(boolean offline) {
        call("Network.emulateNetworkConditions", Map.of("offline", offline, "latency", 0, "downloadThroughput", -1, "uploadThroughput", -1));
    }

    public void screenshot(Path file) throws IOException {
        String data = call("Page.captureScreenshot", Map.of("format", "png")).get("data").asString();
        Files.write(file, Base64.getDecoder().decode(data.getBytes(StandardCharsets.US_ASCII)));
    }

    @Override
    public void close() throws InterruptedException {
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor();
        }
    }
}
