package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import ca.gestionchalet.domain.CopyRules;
import ca.gestionchalet.domain.SheetTarget;
import ca.gestionchalet.repository.RecuRepository;
import ca.gestionchalet.service.RecuCopyService;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D-18 : le fichier du Sheet vient de l'année du reçu. Une correspondance existe (2025 et 2026), mais pas pour l'année du reçu : le reçu est conservé, ses photos sont déposées,
 * le Sheet n'est pas appelé, la copie est bloquée pour ce motif. L'année ajoutée à la configuration (la correspondance est lue au démarrage, le test la change dans l'espion)
 * puis le reçu repris, la ligne est écrite.
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
    RecuRepository depot;

    @Autowired
    TransactionTemplate transaction;

    @Autowired
    InMemoryGoogle google;

    @MockitoSpyBean
    CopyRules rules;

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

    private String etat(Object id) {
        return base.queryForObject("select etat_copie from recu where id = ?", String.class, id);
    }

    @Test
    void ca32_uneAnneeAbsenteDeLaCorrespondanceGardeLeRecuDeposeLesPhotosSansAppelerLeSheetPuisLaLigneEstEcriteQuandLAnneeEstAjoutee() {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", "2024-05-02");

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png(4), RecuRequests.png(5));

        assertThat(reponse.statut()).isEqualTo(201);
        Object id = reponse.json().get("id");
        assertThat(copie.processNext()).isTrue();
        assertThat(etat(id)).isEqualTo("BLOQUEE");
        assertThat(base.queryForObject("select motif_blocage from recu where id = ?", String.class, id)).isEqualTo("ANNEE_NON_CONFIGUREE");
        assertThat(base.queryForObject("select count(drive_file_id) from photo where recu_id = ?", Long.class, id)).isEqualTo(2L);
        assertThat(google.files()).hasSize(2);
        assertThat(google.writeCalls()).as("le Sheet n'est pas appelé").isZero();
        assertThat(google.rows()).isEmpty();

        SheetTarget comptes2024 = new SheetTarget("fichier-essai-2024", "Comptes 2024");
        doReturn(Optional.of(comptes2024)).when(rules).sheetFor(2024);
        transaction.executeWithoutResult(statut -> depot.findById(UUID.fromString((String) id)).orElseThrow().reprendre());
        assertThat(copie.processNext()).isTrue();

        assertThat(etat(id)).isEqualTo("COPIEE");
        assertThat(google.targetOf((String) id)).isEqualTo(comptes2024);
        assertThat(google.rows()).hasSize(1);
        assertThat(google.depositCalls()).as("les photos ne sont pas redéposées").isEqualTo(2);
    }

    @Test
    void ca32_d63_sansAutorisationGoogleUneAnneeAbsenteAttendLAutorisationCarElleSeuleLaisseDeposerLesPhotos() {
        base.update("delete from autorisation_google");
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", "2024-05-02");

        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("etatCopie", "BLOQUEE").containsEntry("motifBlocage", "AUTORISATION_GOOGLE");
        assertThat(google.journal()).isEmpty();
    }
}
