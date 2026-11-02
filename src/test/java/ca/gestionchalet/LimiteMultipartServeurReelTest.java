package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.ServeurReelTest;
import java.net.http.HttpRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** La limite est abaissée pour la franchir sans allouer 75 Mo ; ValeursParDefautTest garde la valeur par défaut. */
@ServeurReelTest
@TestPropertySource(properties = "spring.servlet.multipart.max-request-size=1KB")
class LimiteMultipartServeurReelTest {

    @LocalServerPort
    int port;

    private static String multipart(int octets) {
        return "--x\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"a.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n"
                + "y".repeat(octets) + "\r\n--x--\r\n";
    }

    @Test
    void cs08_multipartTropGrosDonne413Generique() {
        ClientEssai client = new ClientEssai(port, "source-multipart");
        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);

        Reponse reponse = client.envoyer(client.requete("/api/essai/photos")
                .header("Content-Type", "multipart/form-data; boundary=x")
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString(multipart(2048)))
                .build());

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
        assertThat(reponse.enTete("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
