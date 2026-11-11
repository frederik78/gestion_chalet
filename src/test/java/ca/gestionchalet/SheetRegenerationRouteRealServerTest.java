package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;

import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.RegenerationRules;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Contrat HTTP de la régénération (PRD, section 7.1), sur serveur réel : {@code POST /api/sheet/regeneration} et {@code GET /api/sheet/etat}. */
@RealServerTest
class SheetRegenerationRouteRealServerTest {

    private static final String FICHIER_2026 = "fichier-essai";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    InMemoryGoogle google;

    @MockitoSpyBean
    RegenerationRules rules;

    private TestClient client;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from onglet_genere_cellule");
        base.update("delete from onglet_genere");
        base.update("delete from regeneration_annee_non_configuree");
        base.update("delete from regeneration");
        base.update("delete from recu_alerte");
        base.update("delete from photo");
        base.update("delete from recu");
        google.reset();
        google.seedFile(FICHIER_2026);
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private void valider(String date) {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", date);
        assertThat(RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png()).statut()).isEqualTo(201);
    }

    private Reponse etatApresLaFin() {
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        Reponse etat = client.get("/api/sheet/etat");
        while ("EN_COURS".equals(etat.json().get("etat"))) {
            if (System.nanoTime() > limite) {
                fail("La régénération n'est pas terminée");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrompu");
            }
            etat = client.get("/api/sheet/etat");
        }
        return etat;
    }

    @Test
    void ca32_sansRegenerationLEtatEstJamaisAvecSesChampsAVideEtAucunNomNiIdentifiantGoogle() {
        Reponse etat = client.get("/api/sheet/etat");

        assertThat(etat.statut()).isEqualTo(200);
        assertThat(etat.json()).containsOnlyKeys("etat", "motif", "anneeEnCause", "demandeeLe", "derniereReussiteLe", "dernierEchecLe", "recusEcrits", "anneesNonConfigurees")
                .containsEntry("etat", "JAMAIS").containsEntry("anneesNonConfigurees", List.of());
        assertThat(etat.json().values()).filteredOn(valeur -> valeur != null && !valeur.equals("JAMAIS") && !List.of().equals(valeur)).isEmpty();
    }

    @Test
    void ca32_laDemandeRepond202PuisLEtatDevientReussieAvecLesRecusEcritsEtLesAnneesSansFichier() {
        valider("2026-03-05");
        valider("2024-05-02");

        Reponse demande = client.appelAvecJeton("POST", "/api/sheet/regeneration", "");

        assertThat(demande.statut()).isEqualTo(202);
        Reponse etat = etatApresLaFin();
        assertThat(etat.statut()).isEqualTo(200);
        assertThat(etat.json()).containsEntry("etat", "REUSSIE").containsEntry("motif", null).containsEntry("recusEcrits", 1)
                .containsEntry("anneesNonConfigurees", List.of(2024));
        assertThat(etat.json().get("demandeeLe")).isNotNull();
        assertThat(etat.json().get("derniereReussiteLe")).isNotNull();
        assertThat(etat.json().get("dernierEchecLe")).isNull();
        assertThat(etat.corps()).as("ni nom de fichier ou d'onglet, ni contenu mémorisé").doesNotContain("fichier-essai").doesNotContain("Reçus générés").doesNotContain("cellule")
                .doesNotContain("ongletId");
    }

    @Test
    void ca89_uneEcritureEnPanneDonneLEtatEchecGoogleIndisponibleSansDerniereReussite() {
        valider("2026-03-05");
        google.failNext("fill", new TransientGoogleException("HTTP_503"));

        assertThat(client.appelAvecJeton("POST", "/api/sheet/regeneration", "").statut()).isEqualTo(202);

        Reponse etat = etatApresLaFin();
        assertThat(etat.json()).containsEntry("etat", "ECHEC").containsEntry("motif", "GOOGLE_INDISPONIBLE").containsEntry("derniereReussiteLe", null)
                .containsEntry("recusEcrits", null);
        assertThat(etat.json().get("dernierEchecLe")).isNotNull();
    }

    @Test
    void cs01_laRegenerationExigeUneSessionEtLeJetonCsrf() {
        TestClient anonyme = new TestClient(port, "source-" + UUID.randomUUID());

        assertThat(anonyme.get("/api/sheet/etat").statut()).isEqualTo(401);
        assertThat(anonyme.appelAvecJeton("POST", "/api/sheet/regeneration", "").statut()).isEqualTo(401);
        assertThat(client.appelSansJeton("POST", "/api/sheet/regeneration", "", null).statut()).isEqualTo(403);
        assertThat(google.requests()).as("aucune demande n'est partie").isEmpty();
    }
}
