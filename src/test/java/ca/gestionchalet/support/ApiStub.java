package ca.gestionchalet.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/** Serveur HTTP local qui double les API REST de Drive et de Sheets : il garde chaque requête reçue et répond ce que le test décide. Aucun appel ne quitte la machine. */
public final class ApiStub implements AutoCloseable {

    /** Une requête reçue, corps compris. */
    public record Request(String method, String path, String query, Map<String, List<String>> headers, byte[] body) {

        public String bodyText() {
            return new String(body, StandardCharsets.ISO_8859_1);
        }

        public String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(entry -> entry.getValue().getFirst()).findFirst().orElse(null);
        }

        public boolean is(String method, String path) {
            return this.method.equals(method) && this.path.equals(path);
        }
    }

    /** Une réponse JSON, sauf indication contraire. */
    public record Response(int status, String body, String type) {

        public static Response json(String body) {
            return new Response(200, body, "application/json");
        }

        public static Response status(int status) {
            return new Response(status, "{\"error\":{\"message\":\"x\"}}", "application/json");
        }

        public static Response text(int status, String body) {
            return new Response(status, body, "text/html");
        }
    }

    private final HttpServer server;
    private final ExecutorService threads = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        return thread;
    });
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile Function<Request, Response> handler = request -> Response.status(404);

    public ApiStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(threads);
        server.start();
    }

    public String address() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void answer(Function<Request, Response> handler) {
        this.handler = handler;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public void forget() {
        requests.clear();
    }

    /** Plus personne n'écoute : la connexion est refusée. */
    public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery(),
                    Map.copyOf(exchange.getRequestHeaders()), exchange.getRequestBody().readAllBytes());
            requests.add(request);
            Response response = handler.apply(request);
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", response.type());
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        threads.shutdownNow();
    }
}
