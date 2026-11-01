package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import java.net.http.HttpRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Sans le double de {@code getRemoteAddr} de {@link ConfigurationEssai} : la source est la vraie adresse de la connexion, comme
 * en production. Un en-tête fourni par le client ne doit pas changer cette source, quelle que soit la valve ou le filtre actif.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "recus.securite.condense-mot-de-passe=" + ConfigurationEssai.CONDENSE)
class SourceReelleServeurReelTest {

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
        ClientEssai client = new ClientEssai(port, null);
        for (int essai = 0; essai < ECHECS_PAR_DEFAUT; essai++) {
            client.connecter("faux");
        }
        assertThat(client.connecter("faux").statut()).isEqualTo(429);

        Reponse reponse = client.envoyer(client.requete("/api/connexion")
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .header(enTete, valeur)
                .POST(HttpRequest.BodyPublishers.ofString("{\"motDePasse\":\"" + ConfigurationEssai.MOT_DE_PASSE + "\"}"))
                .build());

        assertThat(reponse.statut()).isEqualTo(429);
    }
}
