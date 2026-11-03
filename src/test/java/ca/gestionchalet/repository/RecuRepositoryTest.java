package ca.gestionchalet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.domain.Photo;
import ca.gestionchalet.domain.PhotoFormat;
import ca.gestionchalet.domain.Recu;
import ca.gestionchalet.domain.Valeurs;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base SQLite temporaire, schéma appliqué par Liquibase ; aucune base embarquée ne la remplace. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecuRepositoryTest {

    @Autowired
    RecuRepository depot;

    @Autowired
    JdbcTemplate base;

    @DynamicPropertySource
    static void baseTemporaire(DynamicPropertyRegistry proprietes) throws IOException {
        String fichier = Files.createTempDirectory("recu-repository-test").resolve("base.db").toString();
        proprietes.add("spring.datasource.url", () -> "jdbc:sqlite:" + fichier + "?foreign_keys=true");
    }

    private static Recu recu(UUID cle) {
        Valeurs valeurs = new Valeurs(LocalDate.of(2026, 10, 11), "F", null, "Fournitures", null, null, null, 1150L, null, null);
        return Recu.valider(cle, valeurs, "empreinte", null, Instant.parse("2026-10-11T12:00:00Z"), List.of(Photo.of(1, PhotoFormat.PNG, 10L)));
    }

    @Test
    void ca18_leSchemaVientDeLiquibaseEtUnRecuSeRetrouveParSaCle() {
        UUID cle = UUID.randomUUID();
        Recu enregistre = depot.saveAndFlush(recu(cle));

        assertThat(base.queryForObject("select count(*) from databasechangelog", Long.class)).isPositive();
        assertThat(depot.findByCleIdempotence(cle)).get().extracting(Recu::getId).isEqualTo(enregistre.getId());
        assertThat(depot.findByCleIdempotence(UUID.randomUUID())).isEmpty();
    }

    @Test
    void ca18_laBaseRefuseUneSecondeLigneAvecLaMemeCle() {
        UUID cle = UUID.randomUUID();
        depot.saveAndFlush(recu(cle));

        // Le dialecte SQLite de Hibernate ne traduit pas ce refus en DataIntegrityViolationException : mesuré, JpaSystemException.
        assertThatThrownBy(() -> depot.saveAndFlush(recu(cle))).hasMessageContaining("UNIQUE constraint failed: recu.cle_idempotence");
    }
}
