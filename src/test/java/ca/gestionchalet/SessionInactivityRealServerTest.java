package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import ca.gestionchalet.support.RealServerTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** La durée d'inactivité vient de la configuration ; le serveur réel fait expirer la session. */
@RealServerTest
@TestPropertySource(properties = "server.servlet.session.timeout=1m")
class SessionInactivityRealServerTest {

    @LocalServerPort
    int port;

    @Test
    void cs06_laSessionRecoitLaDureeConfiguree() {
        TestClient client = new TestClient(port, "source-duree");
        client.connecter(TestDoublesConfiguration.MOT_DE_PASSE);

        assertThat(client.get("/api/essai/inactivite").corps()).isEqualTo("60");
    }

    @Test
    void cs06_uneSessionExpireeDonne401() throws InterruptedException {
        TestClient client = new TestClient(port, "source-inactivite");
        client.connecter(TestDoublesConfiguration.MOT_DE_PASSE);
        assertThat(client.get("/api/essai/expirer-bientot").statut()).isEqualTo(200);

        Thread.sleep(2100);

        Reponse reponse = client.get("/api/essai/session");
        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));
    }
}
