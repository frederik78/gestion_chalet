package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientBrut;
import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ConfigurationEssai;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Le délai de lecture est abaissé à 1 s pour le franchir vite ; le délai réel de Tomcat, sans réglage, est de 60 s. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "recus.securite.condense-mot-de-passe=" + ConfigurationEssai.CONDENSE,
    "server.tomcat.connection-timeout=1s"
})
class CorpsJamaisTermineServeurReelTest {

    private static final int ECHECS_PAR_DEFAUT = 5;

    @LocalServerPort
    int port;

    @Test
    void cs05_unCorpsJamaisTermineEstFermeSansReponseEtCompteUneTentative() {
        ClientEssai client = new ClientEssai(port, null);
        client.get("/actuator/health");
        String jeton = client.cookie("XSRF-TOKEN");

        String recu = ClientBrut.lireJusqua(port, "POST /api/connexion HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: 1000\r\n"
                + "X-XSRF-TOKEN: " + jeton + "\r\nCookie: XSRF-TOKEN=" + jeton + "\r\n\r\nmotDePasse=x", "jamais-recu", 5_000);

        assertThat(recu).isEmpty();
        for (int essai = 1; essai < ECHECS_PAR_DEFAUT; essai++) {
            assertThat(client.connecter("faux").statut()).as("échec n° %d", essai).isEqualTo(401);
        }
        assertThat(client.connecter("faux").statut()).isEqualTo(429);
    }
}
