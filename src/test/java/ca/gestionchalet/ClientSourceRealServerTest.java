package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Sans le double de {@code getRemoteAddr} de {@link TestDoublesConfiguration} : la source est la vraie adresse de la connexion, comme
 * en production. Un en-tête fourni par le client ne doit pas changer cette source, quelle que soit la valve ou le filtre actif.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE)
class ClientSourceRealServerTest {

    private static final int ECHECS_PAR_DEFAUT = 5;

    @LocalServerPort
    int port;

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "X-Forwarded-For  | 203.0.113.9",
            "Forwarded        | for=203.0.113.9",
            "X-Real-IP        | 203.0.113.9",
            "CF-Connecting-IP | 203.0.113.9",
    })
    void cs05_unEnTeteDuClientNeLeveNiNeContourneLeBlocage(String enTete, String valeur) {
        TestClient client = new TestClient(port, null);
        for (int essai = 0; essai < ECHECS_PAR_DEFAUT; essai++) {
            client.connecter("faux");
        }
        assertThat(client.connecter("faux").statut()).isEqualTo(429);

        Reponse reponse = client.envoyer(client.requeteConnexion(TestDoublesConfiguration.MOT_DE_PASSE)
                .header(enTete, valeur)
                .build());

        assertThat(reponse.statut()).isEqualTo(429);
    }
}
