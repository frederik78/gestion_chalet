package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.security.DomainUserDetailsService;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/** L'application complète refuse de démarrer sans condensé fort, et n'a aucun compte par défaut. */
@ExtendWith(OutputCaptureExtension.class)
class DemarrageSansCondenseTest {

    private static ConfigurableApplicationContext demarrer(String... proprietes) {
        String[] tout = Arrays.copyOf(proprietes, proprietes.length + 1);
        tout[proprietes.length] = "server.port=0";
        return new SpringApplicationBuilder(GestionChaletApplication.class).properties(tout).run();
    }

    @Test
    void cs07_condenseAbsentEmpecheLeDemarrage() {
        assertThatThrownBy(DemarrageSansCondenseTest::demarrer)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("Condensé du mot de passe absent ou trop faible");
    }

    @ParameterizedTest
    @ValueSource(strings = {"5f4dcc3b5aa765d61d8327deb882cf99", "{noop}motdepasse", "$2a$99$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234"})
    void cs07_algorithmeFaibleEmpecheLeDemarrage(String condense) {
        assertThatThrownBy(() -> demarrer("recus.securite.condense-mot-de-passe=" + condense))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("Condensé du mot de passe absent ou trop faible");
    }

    /** Le seul compte est le compte local : Spring Boot n'en génère aucun, et n'affiche donc aucun mot de passe. */
    @Test
    void cs07_aucunCompteParDefautAvecUnCondenseValide(CapturedOutput journal) {
        String condense = new BCryptPasswordEncoder(10).encode("secret");
        try (ConfigurableApplicationContext contexte = demarrer("recus.securite.condense-mot-de-passe=" + condense)) {
            assertThat(contexte.getBeansOfType(UserDetailsService.class).values()).singleElement().isInstanceOf(DomainUserDetailsService.class);
            assertThat(contexte.getBeanNamesForType(InMemoryUserDetailsManager.class)).isEmpty();
            assertThat(journal.getOut()).doesNotContain("generated security password");
        }
    }
}
