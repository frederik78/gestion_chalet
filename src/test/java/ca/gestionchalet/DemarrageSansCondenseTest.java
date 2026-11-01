package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** L'application complète refuse de démarrer sans condensé fort, et n'a aucun compte par défaut. */
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
    @ValueSource(strings = {"5f4dcc3b5aa765d61d8327deb882cf99", "{noop}motdepasse"})
    void cs07_algorithmeFaibleEmpecheLeDemarrage(String condense) {
        assertThatThrownBy(() -> demarrer("recus.securite.condense-mot-de-passe=" + condense))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("Condensé du mot de passe absent ou trop faible");
    }

    @Test
    void cs07_aucunCompteParDefautAvecUnCondenseValide() {
        String condense = new BCryptPasswordEncoder(10).encode("secret");
        try (ConfigurableApplicationContext contexte = demarrer("recus.securite.condense-mot-de-passe=" + condense)) {
            assertThat(contexte.getBeansOfType(UserDetailsService.class)).isEmpty();
        }
    }
}
