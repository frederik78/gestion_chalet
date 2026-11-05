package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** D-64 : une connexion refusée par le client réel du modèle (adresse locale sans serveur, aucun appel externe) est classée {@code UNREACHABLE} par sa cause. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE,
    "recus.lecture.delai=2s",
    "spring.ai.anthropic.api-key=cle-essai",
})
@ExtendWith(OutputCaptureExtension.class)
class ReadingUnreachableRealServerTest {

    @DynamicPropertySource
    static void adresseSansServeur(DynamicPropertyRegistry registre) {
        registre.add("spring.ai.anthropic.base-url", () -> "http://localhost:" + portLibre());
    }

    private static int portLibre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @LocalServerPort
    int port;

    @Test
    void ca04_uneConnexionRefuseeDonneEchecInjoignable(CapturedOutput journaux) {
        TestClient client = new TestClient(port, null);
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);

        Reponse reponse = RecuRequests.lire(client, "image/jpeg", "facture-perso.jpg", RecuRequests.jpeg(8), RecuRequests.png(8));

        assertThat(reponse.json()).containsEntry("resultat", "ECHEC").containsEntry("champsRejetes", List.of());
        assertThat(journaux.getOut()).contains("Lecture en échec : UNREACHABLE");
    }
}
