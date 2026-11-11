package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.service.RecuCopyService;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D-18 : le fichier du Sheet vient de l'année du reçu. Une correspondance existe (2025 et 2026), mais pas pour l'année du reçu : le reçu est conservé, ses photos sont déposées
 * et le Sheet n'est pas appelé (la suite de CA32, l'onglet généré, est celle de la régénération).
 */
@RealServerTest
class RecuCopyYearNotConfiguredRealServerTest {

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
    void ca32_uneAnneeAbsenteDeLaCorrespondanceGardeLeRecuEtDeposeSesPhotosSansAppelerLeSheet() {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", "2024-05-02");

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png(4), RecuRequests.png(5));

        assertThat(reponse.statut()).isEqualTo(201);
        Object id = reponse.json().get("id");
        copie.processPending();
        assertThat(base.queryForObject("select count(drive_file_id) from photo where recu_id = ?", Long.class, id)).isEqualTo(2L);
        assertThat(google.files()).hasSize(2);
        assertThat(google.writeCalls()).as("le Sheet n'est pas appelé").isZero();
    }
}
