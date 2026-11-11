package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;

import ca.gestionchalet.config.ApplicationProperties;
import ca.gestionchalet.repository.PhotoStorage;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Pannes provoquées : l'écriture de la seconde photo, et une vraie erreur de SQLite levée par un déclencheur. Aucune trace, aucun journal de reçu. */
@RealServerTest
@ExtendWith(OutputCaptureExtension.class)
class RecuFailureRealServerTest {

    private static final String TEMOIN_FOURNISSEUR = "Fournisseur-temoin-journal";
    private static final String TEMOIN_OBJET = "Objet-temoin-journal";
    private static final String TEMOIN_PANNE = "valeur-temoin-dans-la-panne";
    private static final Map<String, String> ERREUR_INTERNE = Map.of("erreur", "Une erreur interne est survenue.", "code", "ERREUR_INTERNE");

    @LocalServerPort
    int port;

    @Autowired
    ApplicationProperties properties;

    @Autowired
    JdbcTemplate base;

    @MockitoSpyBean
    PhotoStorage stockage;

    private TestClient client;

    @BeforeEach
    void connecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    @AfterEach
    void retirerLaPanneDeBase() {
        base.execute("drop trigger if exists panne_photo");
    }

    private static Map<String, Object> corps() {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", TEMOIN_FOURNISSEUR);
        valeurs.put("objet", TEMOIN_OBJET);
        return RecuRequests.corps(valeurs);
    }

    private List<Path> fichiers() throws IOException {
        try (Stream<Path> chemins = Files.walk(properties.dossierPhotos())) {
            return chemins.filter(Files::isRegularFile).toList();
        }
    }

    private long lignes(String table) {
        return base.queryForObject("select count(*) from " + table, Long.class);
    }

    private void verifierQuIlNeResteRien(List<Path> fichiersAvant, long recusAvant, long photosAvant) throws IOException {
        assertThat(fichiers()).isEqualTo(fichiersAvant);
        assertThat(lignes("recu")).isEqualTo(recusAvant);
        assertThat(lignes("photo")).isEqualTo(photosAvant);
    }

    private static void verifierLesJournaux(CapturedOutput journaux) {
        assertThat(journaux.getAll()).doesNotContain(TEMOIN_FOURNISSEUR, TEMOIN_OBJET, TEMOIN_PANNE, "recu.png");
    }

    @Test
    void ca18_unConflitDeCleNEcritAucuneValeurDuRecuDansLesJournaux(CapturedOutput journaux) {
        Map<String, Object> corps = corps();
        byte[] photo = RecuRequests.png();
        assertThat(RecuRequests.valider(client, corps, photo).statut()).isEqualTo(201);
        Map<String, Object> corrige = new HashMap<>(corps);
        Map<String, Object> valeurs = new HashMap<>(RecuRequests.valeurs());
        valeurs.put("fournisseur", TEMOIN_FOURNISSEUR + "-corrige");
        valeurs.put("objet", TEMOIN_OBJET);
        corrige.put("valeurs", valeurs);

        assertThat(RecuRequests.valider(client, corrige, photo).statut()).isEqualTo(409);

        verifierLesJournaux(journaux);
    }

    @Test
    void ca19_lEchecDEcritureDUnePhotoNeLaisseNiRecuNiFichierEtLaMemeCleReussitEnsuite(CapturedOutput journaux) throws IOException {
        Map<String, Object> corps = corps();
        List<Path> fichiersAvant = fichiers();
        long recusAvant = lignes("recu");
        long photosAvant = lignes("photo");
        doCallRealMethod()
                .doThrow(new UncheckedIOException(new IOException(TEMOIN_PANNE)))
                .when(stockage).write(any(), any());

        Reponse panne = RecuRequests.valider(client, corps, RecuRequests.png(), RecuRequests.png(8));

        assertThat(panne.statut()).isEqualTo(500);
        assertThat(panne.json()).isEqualTo(ERREUR_INTERNE);
        verifierQuIlNeResteRien(fichiersAvant, recusAvant, photosAvant);
        verifierLesJournaux(journaux);

        Mockito.reset(stockage);
        Reponse nouvelEssai = RecuRequests.valider(client, corps, RecuRequests.png(), RecuRequests.png(8));

        assertThat(nouvelEssai.statut()).isEqualTo(201);
        assertThat(fichiers()).hasSize(fichiersAvant.size() + 2);
        assertThat(lignes("recu")).isEqualTo(recusAvant + 1);
    }

    @Test
    void ca19_lEchecDeLaBaseNeLaisseNiRecuNiFichierEtLaMemeCleReussitEnsuite(CapturedOutput journaux) throws IOException {
        Map<String, Object> corps = corps();
        List<Path> fichiersAvant = fichiers();
        long recusAvant = lignes("recu");
        long photosAvant = lignes("photo");
        base.execute("create trigger panne_photo before insert on photo begin select raise(abort, '" + TEMOIN_PANNE + "'); end");

        Reponse panne = RecuRequests.valider(client, corps, RecuRequests.png());

        assertThat(panne.statut()).isEqualTo(500);
        assertThat(panne.json()).isEqualTo(ERREUR_INTERNE);
        verifierQuIlNeResteRien(fichiersAvant, recusAvant, photosAvant);
        verifierLesJournaux(journaux);

        retirerLaPanneDeBase();
        Reponse nouvelEssai = RecuRequests.valider(client, corps, RecuRequests.png());

        assertThat(nouvelEssai.statut()).isEqualTo(201);
        assertThat(fichiers()).hasSize(fichiersAvant.size() + 1);
    }

    @Test
    void unCorpsIllisibleOuInvalideNAtteintPasLesJournaux(CapturedOutput journaux) {
        String illisible = "{\"cleIdempotence\":\"" + UUID.randomUUID() + "\",\"valeurs\":{\"fournisseur\":\"" + TEMOIN_FOURNISSEUR + "\",";
        assertThat(RecuRequests.validerJson(client, illisible, RecuRequests.png()).statut()).isEqualTo(400);

        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", TEMOIN_FOURNISSEUR + "x".repeat(200));
        assertThat(RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png()).statut()).isEqualTo(400);

        verifierLesJournaux(journaux);
    }
}
