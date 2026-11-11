package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import ca.gestionchalet.support.RealServerTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/** Mode dev (profil Spring explicite, HTTP sur le poste ou le réseau local) : les cookies ne sont pas {@code Secure}. */
@RealServerTest
@ActiveProfiles("dev")
class CookieOnLocalWorkstationTest {

    @LocalServerPort
    int port;

    @Test
    void cs29_modeDev_leProfilDevRetireSecureDesCookies() {
        TestClient client = new TestClient(port, "source-poste-local");

        Reponse reponse = client.connecter(TestDoublesConfiguration.MOT_DE_PASSE);

        assertThat(reponse.statut()).isEqualTo(204);
        assertThat(reponse.setCookie("JSESSIONID").getFirst()).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        assertThat(reponse.setCookie("XSRF-TOKEN").getLast()).contains("SameSite=Lax").doesNotContain("Secure");
    }
}
