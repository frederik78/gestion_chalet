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
@TestPropertySource(properties = "server.servlet.session.timeout=1m")
class InactiviteSessionServeurReelTest {

    @LocalServerPort
    int port;

    @Test
    void cs06_laSessionRecoitLaDureeConfiguree() {
        ClientEssai client = new ClientEssai(port, "source-duree");
        client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(client.get("/api/essai/inactivite").corps()).isEqualTo("60");
    }

    @Test
    void cs06_uneSessionExpireeDonne401() throws InterruptedException {
        ClientEssai client = new ClientEssai(port, "source-inactivite");
        client.connecter(ConfigurationEssai.MOT_DE_PASSE);
        assertThat(client.get("/api/essai/expirer-bientot").statut()).isEqualTo(200);

        Thread.sleep(2100);

        Reponse reponse = client.get("/api/essai/session");
        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));
    }
}
