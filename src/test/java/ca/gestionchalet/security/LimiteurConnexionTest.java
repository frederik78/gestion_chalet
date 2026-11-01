package ca.gestionchalet.security;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.HorlogeEssai;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LimiteurConnexionTest {

    private final HorlogeEssai horloge = new HorlogeEssai();
    private final LimiteurConnexion limiteur = new LimiteurConnexion(3, Duration.ofMinutes(15), 2, horloge);

    private void echouer(String source, int fois) {
        for (int i = 0; i < fois; i++) {
            limiteur.enregistrerEchec(source);
        }
    }

    @Test
    void cs05_justeAvantLaLimiteLaSourceNEstPasBloquee() {
        echouer("a", 2);

        assertThat(limiteur.blocageRestant("a")).isEmpty();
    }

    @Test
    void cs05_aLaLimiteLaSourceEstBloqueePourLaDureeConfiguree() {
        echouer("a", 3);

        assertThat(limiteur.blocageRestant("a")).contains(Duration.ofMinutes(15));
    }

    @Test
    void cs05_leBlocageSeLeveExactementAuDelai() {
        echouer("a", 3);

        horloge.avancer(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(limiteur.blocageRestant("a")).contains(Duration.ofSeconds(1));

        horloge.avancer(Duration.ofSeconds(1));
        assertThat(limiteur.blocageRestant("a")).isEmpty();
    }

    @Test
    void cs05_lesEchecsAnciensNeSeCumulentPasAuDelaDuDelai() {
        echouer("a", 2);
        horloge.avancer(Duration.ofMinutes(15));

        echouer("a", 1);

        assertThat(limiteur.blocageRestant("a")).isEmpty();
    }

    @Test
    void cs05_uneAutreSourceNEstPasBloquee() {
        echouer("a", 3);

        assertThat(limiteur.blocageRestant("b")).isEmpty();
    }

    @Test
    void cs05_uneReussiteEffaceLesEchecs() {
        echouer("a", 2);

        limiteur.reinitialiser("a");
        echouer("a", 2);

        assertThat(limiteur.blocageRestant("a")).isEmpty();
    }

    @Test
    void cs05_lePlusAncienEstOublieQuandLaMemoireEstPleine() {
        echouer("a", 3);
        horloge.avancer(Duration.ofSeconds(1));
        echouer("b", 3);
        horloge.avancer(Duration.ofSeconds(1));

        echouer("c", 3);

        assertThat(limiteur.sourcesSuivies()).isEqualTo(2);
        assertThat(limiteur.blocageRestant("a")).isEmpty();
        assertThat(limiteur.blocageRestant("b")).isPresent();
        assertThat(limiteur.blocageRestant("c")).isPresent();
    }
}
