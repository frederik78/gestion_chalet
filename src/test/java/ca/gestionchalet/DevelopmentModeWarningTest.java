package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.TestDoublesConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Le mode dev se signale au démarrage par un avertissement, sans aucune valeur de configuration. */
@RealServerTest
@ActiveProfiles("dev")
// Un contexte à lui : un contexte déjà démarré par un autre test aurait écrit l'avertissement avant la capture.
@TestPropertySource(properties = "spring.application.name=gestion-chalet-avertissement-dev")
@ExtendWith(OutputCaptureExtension.class)
class DevelopmentModeWarningTest {

    @Test
    void cs37_modeDev_leDemarrageJournaliseUnAvertissementSansValeurSecrete(CapturedOutput sortie) {
        assertThat(sortie.getAll())
                .contains("WARN")
                .contains("MODE DEV ACTIF : cookies non sécurisés (HTTP accepté). Ne jamais l'utiliser en production.")
                .doesNotContain(TestDoublesConfiguration.CONDENSE);
    }
}
