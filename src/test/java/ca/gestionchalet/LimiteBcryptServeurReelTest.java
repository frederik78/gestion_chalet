package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Limite connue de bcrypt, acceptée (D-29) : seuls les 72 premiers octets comptent. Un changement d'algorithme ferait échouer ce test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LimiteBcryptServeurReelTest {

    private static final String SOIXANTE_DOUZE_OCTETS = "a".repeat(72);

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void condense(DynamicPropertyRegistry proprietes) {
        proprietes.add("recus.securite.condense-mot-de-passe", () -> new BCryptPasswordEncoder(10).encode(SOIXANTE_DOUZE_OCTETS));
    }

    @Test
    void cs04_limiteConnueDeBcryptLaSuiteDesSoixanteDouzeOctetsEstIgnoree() {
        ClientEssai client = new ClientEssai(port, null);

        assertThat(client.connecter("a".repeat(71)).statut()).isEqualTo(401);
        assertThat(client.connecter(SOIXANTE_DOUZE_OCTETS + "suite-ignoree").statut()).isEqualTo(204);
    }
}
