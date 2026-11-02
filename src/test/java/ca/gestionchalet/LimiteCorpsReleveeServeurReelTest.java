package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ServeurReelTest;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** Un seul réglage : le relever déplace la borne de la garde et celle de Tomcat, qui répondrait sinon 413 à la place de la garde. */
@ServeurReelTest
@TestPropertySource(properties = "recus.securite.corps-requete-max=128KB")
class LimiteCorpsReleveeServeurReelTest {

    @LocalServerPort
    int port;

    private Reponse formulaire(int octets) {
        ClientEssai client = new ClientEssai(port, "source-" + octets);
        client.get("/actuator/health");
        String debut = "motDePasse=";
        return client.envoyer(client.requeteConnexion("x")
                .POST(HttpRequest.BodyPublishers.ofString(debut + "x".repeat(octets - debut.length()))).build());
    }

    @Test
    void cs08_releverLaBorneDeplaceLesDeuxLimites() {
        assertThat(formulaire(128 * 1024).statut()).isEqualTo(401);
        assertThat(formulaire(128 * 1024 + 1).statut()).isEqualTo(413);
    }
}
