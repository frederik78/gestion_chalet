package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.service.RecuCopyService;
import ca.gestionchalet.support.GoogleStub;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * D-62 : sans fichier ou onglet du Sheet configuré, la copie ne parle pas à Google, même autorisée. Les vrais adaptateurs de Drive et du Sheet, comme les jetons, pointent sur
 * un seul serveur local qui garde chaque requête reçue : il n'en reçoit aucune. Le reçu reste en base, bloqué, et la réponse de validation le dit.
 */
@RealServerTest
@TestPropertySource(properties = {"essai.google-en-memoire=false", "recus.copie.automatique=true", "recus.copie.intervalle=1h", "recus.copie.fichier=", "recus.copie.onglet="})
class RecuCopyUnconfiguredRealServerTest {

    private static final GoogleStub GOOGLE = demarrerGoogle();

    private static GoogleStub demarrerGoogle() {
        try {
            return new GoogleStub();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void adressesDeGoogle(DynamicPropertyRegistry registre) {
        registre.add("recus.google.adresse-consentement", GOOGLE::adresseConsentement);
        registre.add("recus.google.adresse-jeton", GOOGLE::adresseJeton);
        registre.add("recus.copie.adresse-drive", GOOGLE::adresse);
        registre.add("recus.copie.adresse-sheets", GOOGLE::adresse);
    }

    @AfterAll
    static void arreterGoogle() {
        GOOGLE.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    RecuCopyService copie;

    private TestClient client;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        base.update("delete from autorisation_google");
        base.update("insert into autorisation_google (id, etat, jeton_acces_chiffre, jeton_acces_expire_le, jeton_renouvellement_chiffre) "
                + "values (1, 'VALIDE', 'acces', '2999-01-01T00:00:00Z', 'renouvellement')");
        GOOGLE.reinitialiser();
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    @Test
    void d62_sansFichierNiOngletConfiguresAucuneRequeteNePartVersGoogleEtLeRecuResteBloque() throws Exception {
        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("etatCopie", "BLOQUEE").containsEntry("motifBlocage", "FICHIER_INACCESSIBLE");
        Object id = reponse.json().get("id");
        assertThat(base.queryForObject("select etat_copie from recu where id = ?", String.class, id)).isEqualTo("BLOQUEE");
        assertThat(base.queryForObject("select motif_blocage from recu where id = ?", String.class, id)).isEqualTo("FICHIER_INACCESSIBLE");
        assertThat(base.queryForObject("select count(drive_file_id) from photo where recu_id = ?", Long.class, id)).isZero();
        TimeUnit.MILLISECONDS.sleep(500);
        assertThat(GOOGLE.requetes()).as("aucune requête vers Google (jeton, Drive, Sheets)").isEmpty();
    }

    @Test
    void d62_unRecuDejaACopierSansFichierNiOngletEstBloqueAvantToutePhotoEtToutJeton() throws Exception {
        Object id = RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png()).json().get("id");
        base.update("update recu set etat_copie = 'A_COPIER', motif_blocage = null where id = ?", id);

        assertThat(copie.processNext()).isTrue();

        assertThat(base.queryForObject("select etat_copie from recu where id = ?", String.class, id)).isEqualTo("BLOQUEE");
        assertThat(base.queryForObject("select motif_blocage from recu where id = ?", String.class, id)).isEqualTo("FICHIER_INACCESSIBLE");
        assertThat(GOOGLE.requetes()).as("aucune requête vers Google").isEmpty();
    }
}
