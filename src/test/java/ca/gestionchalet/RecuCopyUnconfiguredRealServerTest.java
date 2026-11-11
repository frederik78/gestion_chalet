package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.service.RecuCopyService;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Q-21 (D-68), qui remplace D-62 pour les photos : sans fichier ni onglet du Sheet configuré, la validation répond 201 sans état de copie, et les photos sont déposées dans Drive
 * quand même, sans aucun appel au Sheet.
 */
@RealServerTest
@TestPropertySource(properties = {"recus.copie.annees.2026.fichier=", "recus.copie.annees.2026.onglet=", "recus.copie.annees.2025.fichier=", "recus.copie.annees.2025.onglet="})
class RecuCopyUnconfiguredRealServerTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    RecuCopyService copie;

    @Autowired
    InMemoryGoogle google;

    private TestClient client;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        base.update("delete from autorisation_google");
        base.update("insert into autorisation_google (id, etat, jeton_acces_chiffre, jeton_acces_expire_le, jeton_renouvellement_chiffre) "
                + "values (1, 'VALIDE', 'acces', '2999-01-01T00:00:00Z', 'renouvellement')");
        google.reset();
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    @Test
    void ca45_q21_sansFichierNiOngletConfiguresLesPhotosSontDeposeesSansAucunAppelAuSheet() {
        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).doesNotContainKeys("etatCopie", "motifBlocage", "prochaineTentative");
        Object id = reponse.json().get("id");

        copie.processPending();

        assertThat(base.queryForObject("select count(drive_file_id) from photo where recu_id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(google.depositCalls()).isEqualTo(1);
        assertThat(google.writeCalls()).isZero();
    }
}
