package ca.gestionchalet.security;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.HorlogeEssai;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    /** Beaucoup plus de réservations que le seuil : un compte non atomique en perdrait, et admettrait trop de tentatives. */
    @Test
    void cs05_desReservationsSimultaneesNeDepassentJamaisLeSeuil() throws Exception {
        int seuil = 1_000;
        int fils = 16;
        int parFil = 500;
        LimiteurConnexion charge = new LimiteurConnexion(seuil, Duration.ofMinutes(15), 2, horloge);
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Integer>> admisesParFil = new ArrayList<>();
        try (ExecutorService execution = Executors.newFixedThreadPool(fils)) {
            for (int i = 0; i < fils; i++) {
                admisesParFil.add(execution.submit(() -> {
                    depart.await();
                    int admises = 0;
                    for (int essai = 0; essai < parFil; essai++) {
                        admises += charge.reserver("a").isEmpty() ? 1 : 0;
                    }
                    return admises;
                }));
            }
            depart.countDown();
            int admises = 0;
            for (Future<Integer> admisesDuFil : admisesParFil) {
                admises += admisesDuFil.get();
            }

            assertThat(admises).isEqualTo(seuil);
        }
    }
}
