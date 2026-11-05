package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestClock;
import ca.gestionchalet.support.TestDoublesConfiguration;
import ca.gestionchalet.support.TestRecuExtractor;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** La limite de lectures, abaissée pour être franchie vite ; {@code DefaultValuesTest} garde la vraie valeur. */
@RealServerTest
@TestPropertySource(properties = "recus.lecture.lectures-par-heure=2")
class RecuReadingLimitRealServerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRecuExtractor modele;

    @Autowired
    TestClock horloge;

    @Test
    void cs19_auDelaDeLaLimiteLaReponseEst429SansAppelAuModeleEtUnePhotoRefuseeNeCompteRien() {
        TestClient client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        modele.reset();
        byte[] image = RecuRequests.jpeg(8);

        assertThat(RecuRequests.lire(client, RecuRequests.gif(4)).statut()).isEqualTo(415);
        assertThat(RecuRequests.lire(client, image).statut()).isEqualTo(200);
        horloge.avancer(Duration.ofMinutes(30));
        assertThat(RecuRequests.lire(client, image).statut()).isEqualTo(200);
        assertThat(modele.calls).hasSize(2);

        Reponse refus = RecuRequests.lire(client, image);

        assertThat(refus.statut()).isEqualTo(429);
        assertThat(refus.json()).isEqualTo(Map.of("erreur", "Trop de tentatives. Réessayez plus tard.", "code", "TROP_DE_TENTATIVES"));
        assertThat(refus.enTete("Retry-After")).isEqualTo("1800");
        assertThat(refus.enTete("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(modele.calls).hasSize(2);

        horloge.avancer(Duration.ofMinutes(30));
        assertThat(RecuRequests.lire(client, image).statut()).isEqualTo(200);
        assertThat(modele.calls).hasSize(3);
    }
}
