package ca.gestionchalet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.gestionchalet.security.CompteLocal;
import ca.gestionchalet.security.IdentifiantsInvalidesException;
import ca.gestionchalet.security.LimiteurConnexion;
import ca.gestionchalet.security.TropDeTentativesException;
import ca.gestionchalet.support.HorlogeEssai;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConnexionServiceTest {

    private static final String SOURCE = "source";

    private final CompteLocal compte = mock(CompteLocal.class);
    private final LimiteurConnexion limiteur = new LimiteurConnexion(1, Duration.ofMinutes(15), 10, new HorlogeEssai());
    private final ConnexionService service = new ConnexionService(compte, limiteur);

    /** Avec un seuil de 1 : si la tentative est déjà comptée quand bcrypt démarre, la source est bloquée à cet instant précis. */
    @Test
    void cs05_laTentativeEstCompteeAvantLaVerificationDuMotDePasse() {
        AtomicBoolean bloqueeDurantLaVerification = new AtomicBoolean();
        when(compte.verifier("faux")).thenAnswer(appel -> {
            bloqueeDurantLaVerification.set(limiteur.reserver(SOURCE).isPresent());
            return false;
        });

        assertThatThrownBy(() -> service.connecter("faux", SOURCE)).isInstanceOf(IdentifiantsInvalidesException.class);

        assertThat(bloqueeDurantLaVerification).isTrue();
    }

    @Test
    void cs05_uneSourceBloqueeNeDeclenchePasBcrypt() {
        limiteur.reserver(SOURCE);

        assertThatThrownBy(() -> service.connecter("secret", SOURCE)).isInstanceOf(TropDeTentativesException.class);

        verify(compte, never()).verifier("secret");
    }
}
