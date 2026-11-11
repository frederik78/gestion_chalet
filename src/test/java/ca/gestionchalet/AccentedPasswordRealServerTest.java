package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.TestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Tomcat décode un formulaire en ISO-8859-1 par défaut : un mot de passe accentué doit pourtant être lu en UTF-8. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccentedPasswordRealServerTest {

    private static final String MOT_DE_PASSE = "été-à-Québec";

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void condense(DynamicPropertyRegistry proprietes) {
        proprietes.add("recus.securite.condense-mot-de-passe", () -> new BCryptPasswordEncoder(10).encode(MOT_DE_PASSE));
    }

    @Test
    void cs04_unMotDePasseAccentueEstLuEnUtf8() {
        assertThat(new TestClient(port, null).connecter(MOT_DE_PASSE).statut()).isEqualTo(204);
    }
}
