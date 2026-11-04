package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Un reçu daté de plus de six ans survit au démarrage de l'application. Ne prouve rien d'une tâche lancée plus tard : les règles de {@code AgentRulesTest} s'en chargent. */
class OldReceiptRetentionTest {

    private static final byte[] OCTETS = {1, 2, 3};

    @TempDir
    Path dossier;

    private ConfigurableApplicationContext demarrer() {
        return new SpringApplicationBuilder(GestionChaletApplication.class).run("--server.port=0",
                "--recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE,
                "--spring.datasource.url=jdbc:sqlite:" + dossier.resolve("base.db") + "?foreign_keys=true",
                "--recus.dossier-donnees=" + dossier, "--recus.dossier-photos=" + dossier.resolve("photos"));
    }

    private List<Path> fichiers() throws IOException {
        try (Stream<Path> chemins = Files.list(dossier.resolve("photos"))) {
            return chemins.toList();
        }
    }

    @Test
    void ca72_unRecuDePlusDeSixAnsEtSaPhotoRestentApresUnDemarrage() throws IOException {
        Photo photo = Photo.of(1, PhotoFormat.PNG, OCTETS.length);
        try (ConfigurableApplicationContext contexte = demarrer()) {
            Valeurs valeurs = new Valeurs(LocalDate.of(2018, 1, 15), "Ancien", null, "Fournitures", null, null, null, 1000L, null, null);
            contexte.getBean(PhotoStorage.class).write(photo, new ByteArrayInputStream(OCTETS));
            contexte.getBean(RecuRepository.class).saveAndFlush(
                    Recu.valider(UUID.randomUUID(), valeurs, "empreinte", null, Instant.parse("2018-01-15T12:00:00Z"), List.of(photo), List.of()));
        }

        try (ConfigurableApplicationContext contexte = demarrer()) {
            JdbcTemplate base = contexte.getBean(JdbcTemplate.class);
            assertThat(base.queryForObject("select count(*) from recu where date_recu = '2018-01-15'", Long.class)).isEqualTo(1);
            assertThat(base.queryForObject("select count(*) from photo", Long.class)).isEqualTo(1);
            assertThat(fichiers()).singleElement().satisfies(fichier -> assertThat(Files.readAllBytes(fichier)).isEqualTo(OCTETS));
        }
    }
}
