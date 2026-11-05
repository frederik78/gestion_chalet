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
        "spring.servlet.multipart.max-request-size, 76MB",
        "server.tomcat.max-swallow-size, 100MB",
        "server.tomcat.connection-timeout, 20s",
        "logging.level.org.hibernate.orm.jdbc.error, OFF",
        "recus.photos-par-recu-max, 5",
        "recus.dimensions-max-megapixels, 64",
        "'recus.formats[0]', image/jpeg",
        "'recus.formats[1]', image/png",
        "recus.fuseau-horaire, America/Montreal",
        "recus.alertes.seuil-numeros-inscription-cents, 10000",
        "recus.alertes.seuil-piece-a-verifier-cents, 50000",
        "recus.alertes.ecart-tolere-cents, 2",
        "recus.modele, claude-haiku-5-5",
        "recus.lecture.delai, 60s",
        "recus.lecture.lectures-par-heure, 60",
        "recus.lecture.cote-max-pixels, 1568",
        "recus.lecture.cote-source-max-pixels, 20000",
        "recus.lecture.decodages-simultanes, 1",
        "recus.lecture.passes-decodage-max, 32",
        "recus.lecture.delai-decodage, 20s",
        "recus.lecture.attente-decodage, 30s",
        "spring.ai.anthropic.timeout, ${recus.lecture.delai}",
        "spring.ai.anthropic.max-retries, 0",
        "spring.ai.anthropic.chat.max-tokens, 1024",
        "'recus.types-depense[0]', Honoraires professionnel",
        "'recus.types-depense[10]', Administration",
    })
    void cs06_lesValeursParDefautSontCellesDuPrd(String cle, String attendu) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));

        assertThat(String.valueOf(sources.getFirst().getProperty(cle))).isEqualTo(attendu);
    }
}
