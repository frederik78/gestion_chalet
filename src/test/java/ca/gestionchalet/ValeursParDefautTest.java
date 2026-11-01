package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/** Valeurs de la section 9 du PRD : les tests les abaissent pour les franchir vite, celui-ci garde les vraies. */
class ValeursParDefautTest {

    @ParameterizedTest
    @CsvSource({
        "recus.securite.echecs-avant-blocage, 5",
        "recus.securite.duree-blocage, 15m",
        "recus.securite.inactivite-session, 8h",
        "recus.securite.cookie-securise, true",
        "server.forward-headers-strategy, none",
        "spring.servlet.multipart.max-file-size, 15MB",
        "spring.servlet.multipart.max-request-size, 75MB",
    })
    void cs06_lesValeursParDefautSontCellesDuPrd(String cle, String attendu) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        assertThat(String.valueOf(sources.getFirst().getProperty(cle))).isEqualTo(attendu);
    }
}
