package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.ServeurReelTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** Sur le poste local (HTTP), le cookie n'est pas {@code Secure} ; la valeur par défaut, hors poste local, l'est. */
@ServeurReelTest
@TestPropertySource(properties = "recus.securite.cookie-securise=false")
class CookieHorsPosteLocalTest {

    @LocalServerPort
    int port;

    @Test
    void cs04_laConfigurationDuPosteLocalRetireSecureDesCookies() {
        ClientEssai client = new ClientEssai(port, "source-poste-local");

        Reponse reponse = client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(reponse.statut()).isEqualTo(204);
        assertThat(reponse.setCookie("JSESSIONID").getFirst()).contains("HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        assertThat(reponse.setCookie("XSRF-TOKEN").getLast()).contains("SameSite=Lax").doesNotContain("Secure");
    }
}
