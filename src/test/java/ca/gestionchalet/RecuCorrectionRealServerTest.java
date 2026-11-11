package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;

import ca.gestionchalet.client.SheetTab;
import ca.gestionchalet.domain.RegenerationRules;
import ca.gestionchalet.service.SheetRegenerationService;
import ca.gestionchalet.service.dto.RegenerationStateDTO;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestClock;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

/** La correction d'un reçu validé, par {@code PUT /api/recus/{id}} : les reçus sont validés par l'API, comme le fait la PWA, et la base est lue pour constater ce qui change. */
@RealServerTest
class RecuCorrectionRealServerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    InMemoryGoogle google;

    @Autowired
    SheetRegenerationService regeneration;

    @Autowired
    TestClock horloge;

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
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private static Map<String, Object> valeurs(String fournisseur, Long avantTaxes, Long tps, Long tvq, long total) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("date", "2026-06-16");
        valeurs.put("fournisseur", fournisseur);
        valeurs.put("typeDepense", "Fournitures");
        valeurs.put("montantAvantTaxesCents", avantTaxes);
        valeurs.put("tpsCents", tps);
        valeurs.put("tvqCents", tvq);
        valeurs.put("montantTotalCents", total);
        return valeurs;
    }

    private String valider(Map<String, Object> valeurs) {
        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());
        assertThat(reponse.statut()).isEqualTo(201);
        return (String) reponse.json().get("id");
    }

    private Reponse corriger(String id, Object version, Map<String, Object> valeurs) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("version", version);
        corps.put("valeurs", valeurs);
        return client.appelAvecJeton("PUT", "/api/recus/" + id, JSON.writeValueAsString(corps));
    }

    private Map<String, Object> ligne(String id) {
        return base.queryForMap("select * from recu where id = ?", id);
    }

    @Test
    void ca61_uneVersionPerimeeDonne409VersionPerimeeEtRienNeChange() {
        String id = valider(valeurs("Avant", null, null, null, 3500));
        assertThat(corriger(id, 1, valeurs("Deuxième", null, null, null, 3500)).statut()).isEqualTo(200);
        assertThat(corriger(id, 2, valeurs("Troisième", null, null, null, 3500)).statut()).isEqualTo(200);
        Map<String, Object> avant = ligne(id);

        Reponse reponse = corriger(id, 2, valeurs("Périmée", null, null, null, 9900));

        assertThat(reponse.statut()).isEqualTo(409);
        assertThat(reponse.json()).containsEntry("code", "VERSION_PERIMEE");
        assertThat(avant).containsEntry("version", 3).containsEntry("fournisseur", "Troisième");
        assertThat(ligne(id)).isEqualTo(avant);
    }

    private static final String FICHIER_2026 = "fichier-essai";
    private static final String ONGLET_GENERE = "Reçus générés";
    private static final int NOTES = 8;
    private static final int OBJET = 2;
    private static final int ID = 10;

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> alertes(Reponse reponse) {
        return (List<Map<String, Object>>) reponse.json().get("alertes");
    }

    private static List<Object> codes(Reponse reponse) {
        return alertes(reponse).stream().map(alerte -> alerte.get("code")).toList();
    }

    private List<String> codesEnBase(String id) {
        return base.queryForList("select code from recu_alerte where recu_id = ? order by rang", String.class, id);
    }

    private Map<String, Object> garageIberville(long tvq, long total) {
        Map<String, Object> valeurs = valeurs("Garage Iberville", 61827L, 3091L, tvq, total);
        valeurs.put("numeroInscriptionTps", "123456789RT0001");
        valeurs.put("numeroInscriptionTvq", "1234567890TQ0001");
        return valeurs;
    }

    /** Demande la régénération, comme le bouton, puis attend qu'elle ne soit plus en cours. */
    private RegenerationStateDTO regenerer() {
        regeneration.request();
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (regeneration.state().etat().equals("EN_COURS")) {
            if (System.nanoTime() > limite) {
                fail("La régénération n'est pas terminée");
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrompu");
            }
        }
        return regeneration.state();
    }

    private int identifiantOngletGenere() {
        return google.tabsOf(FICHIER_2026).stream().filter(onglet -> onglet.title().equals(ONGLET_GENERE)).map(SheetTab::id).findFirst().orElseThrow();
    }

    private List<List<Object>> ongletGenere() {
        return google.cellsOf(FICHIER_2026, identifiantOngletGenere());
    }

    @Test
    void ca138_laCorrectionDeLaTvqEtDuTotalRecalculeLesAlertesEtLeRecuRelePorteLeResultat() {
        String id = valider(garageIberville(1667, 66585));
        assertThat(codesEnBase(id)).containsExactly("PIECE_A_VERIFIER", "RAPPORT_TVQ_TPS");

        Reponse reponse = corriger(id, 1, garageIberville(6167, 71085));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(codes(reponse)).as("RAPPORT_TVQ_TPS disparaît, PIECE_A_VERIFIER reste").containsExactly("PIECE_A_VERIFIER");
        assertThat(codesEnBase(id)).containsExactly("PIECE_A_VERIFIER");
        assertThat(ligne(id)).containsEntry("tvq_cents", 6167).containsEntry("montant_total_cents", 71085).containsEntry("version", 2);
    }

    @Test
    void ca138_uneCorrectionPeutFaireApparaitreLesAlertesDeTaxes() {
        String id = valider(garageIberville(6167, 71085));
        assertThat(codesEnBase(id)).containsExactly("PIECE_A_VERIFIER");

        Reponse reponse = corriger(id, 1, garageIberville(1667, 66585));

        assertThat(codes(reponse)).containsExactly("PIECE_A_VERIFIER", "RAPPORT_TVQ_TPS");
        assertThat(alertes(reponse).get(1)).containsEntry("message", "Rapport TVQ / TPS inhabituel : TVQ 16,67 $ pour une TPS de 30,91 $");
    }

    @Test
    void ca28_unRecuSeulDeSonEspeceNEstPasSonPropreDoublonEnSeCorrigeant() {
        Map<String, Object> valeurs = valeurs("Café du Lac", null, null, null, 2500);
        String id = valider(valeurs);

        Reponse reponse = corriger(id, 1, valeurs("Café du Lac", null, null, null, 2500));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(codes(reponse)).doesNotContain("DOUBLON_POSSIBLE");
    }

    @Test
    void ca28_unAutreRecuDeMemeFournisseurDateEtTotalResteUnDoublon() {
        valider(valeurs("Café du Lac", null, null, null, 2500));
        String id = valider(valeurs("Café du Lac", null, null, null, 2600));

        Reponse reponse = corriger(id, 1, valeurs("CAFE DU LAC", null, null, null, 2500));

        assertThat(codes(reponse)).contains("DOUBLON_POSSIBLE");
    }

    @Test
    void ca61_unePutNeCorrigeRienSiUneValeurEstInvalideEtRienNEstEnregistre() {
        String id = valider(valeurs("Avant", null, null, null, 3500));
        Map<String, Object> avant = ligne(id);
        Map<String, Object> futur = valeurs("Futur", null, null, null, 3500);
        futur.put("date", "2999-01-01");

        assertThat(corriger(id, 1, valeurs("", null, null, null, 3500)).statut()).as("fournisseur vide (R-06)").isEqualTo(400);
        assertThat(corriger(id, 1, valeurs("Total nul", null, null, null, 0)).statut()).as("total nul (R-10)").isEqualTo(400);
        assertThat(corriger(id, 1, futur).statut()).as("date future (R-10)").isEqualTo(400);
        assertThat(corriger(id, null, valeurs("Sans version", null, null, null, 3500)).statut()).isEqualTo(400);
        assertThat(corriger(id, "deux", valeurs("Version texte", null, null, null, 3500)).statut()).isEqualTo(400);

        assertThat(ligne(id)).isEqualTo(avant);
    }

    @Test
    void ca61_unRecuInconnuDonne404IntrouvableMemeAvecUneVersionPerimee() {
        Reponse reponse = corriger(UUID.randomUUID().toString(), 7, valeurs("Inconnu", null, null, null, 3500));

        assertThat(reponse.statut()).isEqualTo(404);
        assertThat(reponse.json()).containsEntry("code", "INTROUVABLE");
        assertThat(client.appelAvecJeton("PUT", "/api/recus/pas-un-uuid", "{}").statut()).isEqualTo(400);
    }

    @Test
    void ca61_lEtatLesAlertesLeTotalLuEtLesPhotosViennentDuServeurJamaisDuClient() {
        Map<String, Object> corps = RecuRequests.corps(valeurs("Total lu", null, null, null, 4000));
        corps.put("montantTotalLuCents", 12000);
        String id = (String) RecuRequests.valider(client, corps, RecuRequests.png()).json().get("id");
        Map<String, Object> avant = ligne(id);
        String photoAvant = base.queryForObject("select id || '/' || format || '/' || taille from photo where recu_id = ?", String.class, id);
        Map<String, Object> envoi = new LinkedHashMap<>();
        envoi.put("version", 1);
        envoi.put("valeurs", valeurs("Total lu corrigé", null, null, null, 4500));
        envoi.put("etatCopie", "COPIEE");
        envoi.put("alertes", List.of(Map.of("code", "FAUSSE")));
        envoi.put("montantTotalLuCents", 1);
        envoi.put("photos", List.of());
        envoi.put("id", UUID.randomUUID().toString());
        envoi.put("empreinteContenu", "falsifiee");

        Reponse reponse = client.appelAvecJeton("PUT", "/api/recus/" + id, JSON.writeValueAsString(envoi));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.corps()).doesNotContain("FAUSSE");
        assertThat(codes(reponse)).as("R-13 : le plus élevé du total validé et du total lu enregistré (12000), non celui du client").containsExactly("NUMEROS_INSCRIPTION_MANQUANTS");
        assertThat(ligne(id)).containsEntry("etat_copie", avant.get("etat_copie")).containsEntry("montant_total_lu_cents", 12000)
                .containsEntry("empreinte_contenu", avant.get("empreinte_contenu")).containsEntry("cle_idempotence", avant.get("cle_idempotence")).containsEntry("fournisseur", "Total lu corrigé");
        assertThat(base.queryForObject("select id || '/' || format || '/' || taille from photo where recu_id = ?", String.class, id)).isEqualTo(photoAvant);
        assertThat(base.queryForObject("select count(*) from photo where recu_id = ?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void ca61_laCorrectionNoteSaDateEtLaVersionAvanceDUnSeulPas() {
        String id = valider(valeurs("Date notée", null, null, null, 3500));
        assertThat(ligne(id)).containsEntry("corrige_le", null);
        horloge.avancer(Duration.ofHours(2));

        Reponse reponse = corriger(id, 1, valeurs("Date notée", null, null, null, 3600));

        assertThat(reponse.json()).containsEntry("version", 2);
        assertThat(reponse.json().get("corrigeLe")).isNotNull().isNotEqualTo(reponse.json().get("valideLe"));
        assertThat(ligne(id).get("corrige_le")).isNotNull();
    }

    @Test
    void ca61_unRenvoiDeLaValidationInitialRendToujoursLeRecuApresUneCorrection() {
        Map<String, Object> corps = RecuRequests.corps(valeurs("Renvoi", null, null, null, 3500));
        byte[] photo = RecuRequests.png();
        String id = (String) RecuRequests.valider(client, corps, photo).json().get("id");
        assertThat(corriger(id, 1, valeurs("Renvoi corrigé", null, null, null, 3500)).statut()).isEqualTo(200);

        Reponse renvoi = RecuRequests.valider(client, corps, photo);

        assertThat(renvoi.statut()).as("R-11 : la clé et le contenu validés sont toujours ceux d'origine").isEqualTo(200);
        assertThat(renvoi.json()).containsEntry("id", id);
        assertThat(base.queryForObject("select count(*) from recu", Long.class)).isEqualTo(1L);
    }

    @Test
    void ca98_laCorrectionApparaitALaRegenerationSuivanteSansAucunAppelAGoogleNiPhotoRedeposee() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        Map<String, Object> initiales = valeurs("Quincaillerie", null, null, null, 3500);
        initiales.put("objet", "Ampoules");
        String id = valider(initiales);
        assertThat(regenerer().etat()).isEqualTo("REUSSIE");
        assertThat(ongletGenere().get(1).get(OBJET)).isEqualTo("Ampoules");
        int appelsDepotAvant = google.depositCalls();
        int requetesAvant = google.requests().size();
        int fichiersAvant = google.files().size();
        List<String> photosAvant = base.queryForList("select id || '/' || coalesce(drive_file_id, '') from photo where recu_id = ?", String.class, id);
        Map<String, Object> corrigees = valeurs("Quincaillerie", null, null, null, 3500);
        corrigees.put("objet", "Ampoules DEL");

        Reponse reponse = corriger(id, 1, corrigees);

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(google.requests()).as("la correction n'a causé aucun appel au Sheet").hasSize(requetesAvant);
        assertThat(google.depositCalls()).as("ni dépôt Drive").isEqualTo(appelsDepotAvant);
        assertThat(google.files()).hasSize(fichiersAvant);
        assertThat(ongletGenere().get(1).get(OBJET)).as("le Sheet ne change pas tant qu'on ne régénère pas").isEqualTo("Ampoules");

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(ongletGenere().get(1)).as("l'onglet généré porte la valeur corrigée").satisfies(ligne -> {
            assertThat(ligne.get(OBJET)).isEqualTo("Ampoules DEL");
            assertThat(ligne.get(ID)).isEqualTo(id);
        });
        assertThat(google.depositCalls()).as("la régénération ne dépose rien non plus").isEqualTo(appelsDepotAvant);
        assertThat(base.queryForList("select id || '/' || coalesce(drive_file_id, '') from photo where recu_id = ?", String.class, id)).as("photos ni redéposées ni renommées").isEqualTo(photosAvant);
    }

    @Test
    void ca63_untotalCorrigeDe40A600DollarsAjouteLAlertePieceAVerifierVueDansNotes() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        Map<String, Object> initiales = valeurs("Fournisseur témoin", null, null, null, 4000);
        initiales.put("numeroInscriptionTps", "123456789RT0001");
        initiales.put("numeroInscriptionTvq", "1234567890TQ0001");
        String id = valider(initiales);
        regenerer();
        assertThat(ongletGenere().get(1).get(NOTES)).isNull();
        Map<String, Object> corrigees = valeurs("Fournisseur témoin", null, null, null, 60000);
        corrigees.put("numeroInscriptionTps", "123456789RT0001");
        corrigees.put("numeroInscriptionTvq", "1234567890TQ0001");

        Reponse reponse = corriger(id, 1, corrigees);
        regenerer();

        assertThat(codes(reponse)).containsExactly("PIECE_A_VERIFIER");
        assertThat(ongletGenere().get(1).get(NOTES)).isEqualTo("Pièce à vérifier : nom de l'acheteur, description, conditions de paiement");
    }

    @Test
    void ca62_uneRegenerationDemandeeSansImportEcraseLaModificationManuelleDeLOngletGenereEtLaisseCelleDeComptes2026() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        int comptes = 7;
        google.seedTab(FICHIER_2026, comptes, "Comptes 2026", List.of(List.of("DATE", "BOUTIQUE"), List.of("5 mars 2026", "Saisi à la main")));
        Map<String, Object> initiales = valeurs("Quincaillerie", null, null, null, 3500);
        initiales.put("objet", "Ampoules");
        String id = valider(initiales);
        regenerer();
        google.editCell(FICHIER_2026, identifiantOngletGenere(), 1, OBJET, "Modifié à la main");
        google.editCell(FICHIER_2026, comptes, 1, 1, "Modifié à la main aussi");
        Map<String, Object> corrigees = valeurs("Quincaillerie", null, null, null, 3500);
        corrigees.put("objet", "Ampoules DEL");
        assertThat(corriger(id, 1, corrigees).statut()).isEqualTo(200);
        Map<String, Object> baseAvant = ligne(id);

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(ongletGenere().get(1).get(OBJET)).as("la valeur de la base remplace la modification manuelle").isEqualTo("Ampoules DEL");
        assertThat(google.cellsOf(FICHIER_2026, comptes).get(1)).as("« Comptes 2026 » est intact").containsExactly("5 mars 2026", "Modifié à la main aussi");
        assertThat(ligne(id)).as("la base n'a pas changé").isEqualTo(baseAvant);
    }
}
