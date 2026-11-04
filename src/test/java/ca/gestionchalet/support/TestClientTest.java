package ca.gestionchalet.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TestClientTest {

    /** Sans cookie, le test doit dire ce que le serveur a répondu, et non échouer sur une valeur nulle. */
    @Test
    void sansCookieXsrfLEchecMontreLaReponseRecue() throws IOException {
        HttpServer serveur = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        serveur.createContext("/", echange -> {
            byte[] corps = "corps-temoin".getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("X-Temoin", "en-tete-temoin");
            echange.sendResponseHeaders(503, corps.length);
            echange.getResponseBody().write(corps);
            echange.close();
        });
        serveur.start();
        try {
            TestClient client = new TestClient(serveur.getAddress().getPort(), null);

            assertThatThrownBy(() -> client.connecter("x"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("XSRF-TOKEN")
                    .hasMessageContaining("503")
                    .hasMessageContaining("en-tete-temoin")
                    .hasMessageContaining("corps-temoin");
            assertThat(client.cookie("XSRF-TOKEN")).isNull();
        } finally {
            serveur.stop(0);
        }
    }
}
