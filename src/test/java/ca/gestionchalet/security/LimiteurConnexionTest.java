package ca.gestionchalet.security;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.HorlogeEssai;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class LimiteurConnexionTest {

    private final HorlogeEssai horloge = new HorlogeEssai();
    private final LimiteurConnexion limiteur = new LimiteurConnexion(3, Duration.ofMinutes(15), 2, horloge);

    private void tenter(String source, int fois) {
        for (int i = 0; i < fois; i++) {
            assertThat(limiteur.reserver(source)).as("tentative n° %d", i + 1).isEmpty();
        }
    }

    @Test
    void cs05_jusquAuSeuilLesTentativesSontAdmises() {
        tenter("a", 3);
    }

    @Test
    void cs05_auDelaDuSeuilLaSourceEstBloqueePourLaDureeConfiguree() {
        tenter("a", 3);

        assertThat(limiteur.reserver("a")).contains(Duration.ofMinutes(15));
    }

    @Test
    void cs05_uneTentativeBloqueeNeProlongePasLeBlocage() {
        tenter("a", 3);
        horloge.avancer(Duration.ofMinutes(10));

        assertThat(limiteur.reserver("a")).contains(Duration.ofMinutes(5));
        assertThat(limiteur.reserver("a")).contains(Duration.ofMinutes(5));
    }

    @Test
    void cs05_leBlocageSeLeveExactementAuDelai() {
        tenter("a", 3);

        horloge.avancer(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(limiteur.reserver("a")).contains(Duration.ofSeconds(1));

        horloge.avancer(Duration.ofSeconds(1));
        assertThat(limiteur.reserver("a")).isEmpty();
    }

    @Test
    void cs05_lesTentativesAnciennesNeSeCumulentPasAuDelaDuDelai() {
        tenter("a", 2);
        horloge.avancer(Duration.ofMinutes(15));

        tenter("a", 3);

        assertThat(limiteur.reserver("a")).isPresent();
    }

    @Test
    void cs05_uneAutreSourceNEstPasBloquee() {
        tenter("a", 3);

        assertThat(limiteur.reserver("b")).isEmpty();
    }

    @Test
    void cs05_uneReussiteEffaceLeCompte() {
        tenter("a", 3);

        limiteur.reinitialiser("a");

        tenter("a", 3);
    }

    @Test
    void cs05_lePlusAncienEstOublieQuandLaMemoireEstPleine() {
        tenter("a", 3);
        horloge.avancer(Duration.ofSeconds(1));
        tenter("b", 3);
        horloge.avancer(Duration.ofSeconds(1));

        tenter("c", 3);

        assertThat(limiteur.sourcesSuivies()).isEqualTo(2);
        assertThat(limiteur.reserver("b")).isPresent();
        assertThat(limiteur.reserver("c")).isPresent();
    }

    @Test
    void cs05_desReservationsSimultaneesNeDepassentJamaisLeSeuil() throws Exception {
        int simultanees = 64;
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Optional<Duration>>> reponses = new ArrayList<>();
        try (ExecutorService fils = Executors.newFixedThreadPool(simultanees)) {
            for (int i = 0; i < simultanees; i++) {
                reponses.add(fils.submit(() -> {
                    depart.await();
                    return limiteur.reserver("a");
                }));
            }
            depart.countDown();
            int admises = 0;
            for (Future<Optional<Duration>> reponse : reponses) {
                admises += reponse.get().isEmpty() ? 1 : 0;
            }

            assertThat(admises).isEqualTo(3);
        }
    }
}
