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
class DefaultValuesTest {

    @ParameterizedTest
    @CsvSource({
        "recus.securite.echecs-avant-blocage, 5",
        "recus.securite.duree-blocage, 15m",
        "server.servlet.session.timeout, 8h",
        "recus.securite.corps-requete-max, 64KB",
        "server.tomcat.max-http-form-post-size, ${recus.securite.corps-requete-max}",
        "spring.servlet.multipart.resolve-lazily, true",
        "recus.securite.cookie-securise, true",
        "server.forward-headers-strategy, none",
        "spring.mvc.formcontent.filter.enabled, false",
        "spring.mvc.hiddenmethod.filter.enabled, false",
        "spring.servlet.multipart.max-file-size, 15MB",
        "spring.servlet.multipart.max-request-size, 75MB",
        "logging.level.org.hibernate.orm.jdbc.error, OFF",
        "recus.montant-max-cents, 100000000",
        "recus.fuseau-horaire, America/Montreal",
        "'recus.types-depense[0]', Honoraires professionnel",
        "'recus.types-depense[10]', Administration",
    })
    void cs06_lesValeursParDefautSontCellesDuPrd(String cle, String attendu) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        assertThat(String.valueOf(sources.getFirst().getProperty(cle))).isEqualTo(attendu);
    }
}
