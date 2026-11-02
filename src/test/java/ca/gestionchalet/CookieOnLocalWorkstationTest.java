package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import ca.gestionchalet.support.RealServerTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** Sur le poste local (HTTP), le cookie n'est pas {@code Secure} ; la valeur par défaut, hors poste local, l'est. */
@RealServerTest
@TestPropertySource(properties = "recus.securite.cookie-securise=false")
class CookieOnLocalWorkstationTest {

    @LocalServerPort
    int port;

    @Test
    void cs04_laConfigurationDuPosteLocalRetireSecureDesCookies() {
        TestClient client = new TestClient(port, "source-poste-local");

        Reponse reponse = client.connecter(TestDoublesConfiguration.MOT_DE_PASSE);

        assertThat(reponse.statut()).isEqualTo(204);
        assertThat(reponse.setCookie("JSESSIONID").getFirst()).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        assertThat(reponse.setCookie("XSRF-TOKEN").getLast()).contains("SameSite=Lax").doesNotContain("Secure");
    }
}
