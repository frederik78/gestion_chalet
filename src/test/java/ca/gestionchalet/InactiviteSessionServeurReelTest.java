package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.ServeurReelTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** La durée d'inactivité vient de la configuration ; le serveur réel fait expirer la session. */
@ServeurReelTest
@TestPropertySource(properties = "recus.securite.inactivite-session=2s")
class InactiviteSessionServeurReelTest {

    @LocalServerPort
    int port;

    @Test
    void cs06_auDelaDeLaDureeConfigureeLAncienCookieDonne401() throws InterruptedException {
        ClientEssai client = new ClientEssai(port, "source-inactivite");
        client.connecter(ConfigurationEssai.MOT_DE_PASSE);
        assertThat(client.get("/api/essai/session").statut()).isEqualTo(200);

        Thread.sleep(2100);

        Reponse reponse = client.get("/api/essai/session");
        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));
    }
}
