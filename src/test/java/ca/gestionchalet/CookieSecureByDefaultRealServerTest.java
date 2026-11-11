package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Sans profil explicite, l'application est en mode prod : les cookies de session et CSRF portent {@code Secure}. */
@RealServerTest
class CookieSecureByDefaultRealServerTest {

    @LocalServerPort
    int port;

    @Test
    void cs37_modeProd_sansProfilLesCookiesPortentSecure() {
        TestClient client = new TestClient(port, "source-mode-prod");

        Reponse reponse = client.connecter(TestDoublesConfiguration.MOT_DE_PASSE);

        assertThat(reponse.statut()).isEqualTo(204);
        assertThat(reponse.setCookie("JSESSIONID").getFirst()).contains("HttpOnly", "SameSite=Lax", "Secure");
        assertThat(reponse.setCookie("XSRF-TOKEN").getLast()).contains("SameSite=Lax", "Secure");
    }
}
