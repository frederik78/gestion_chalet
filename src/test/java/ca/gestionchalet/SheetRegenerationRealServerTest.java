package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;

import ca.gestionchalet.client.BlockedGoogleException;
import ca.gestionchalet.client.SheetTab;
import ca.gestionchalet.client.TransientGoogleException;
import ca.gestionchalet.domain.MotifBlocage;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * La régénération du Sheet (D-66, D-68) contre le Sheet doublé en mémoire, avec ses onglets, ses cellules typées et la trace de ses demandes : l'onglet généré reproduit les reçus
 * de la base, tout ou rien, dans le fichier de l'année de chaque reçu. Les reçus sont validés par l'API, comme le fait la PWA.
 */
@RealServerTest
class SheetRegenerationRealServerTest {

    private static final String FICHIER_2026 = "fichier-essai";
    private static final String FICHIER_2025 = "fichier-essai-2025";
    private static final String ONGLET_GENERE = "Reçus générés";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    SheetRegenerationService regeneration;

    @Autowired
    InMemoryGoogle google;

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

    /** Valide un reçu à cette date, une minute après le précédent : l'ordre de validation est celui des appels. */
    private String valider(String date, String fournisseur, String objet) {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", date);
        valeurs.put("fournisseur", fournisseur);
        valeurs.put("objet", objet);
        horloge.avancer(Duration.ofMinutes(1));
        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());
        assertThat(reponse.statut()).isEqualTo(201);
        return (String) reponse.json().get("id");
    }

    /** Demande la régénération, comme le bouton, puis attend qu'elle ne soit plus en cours. */
    private RegenerationStateDTO regenerer() {
        regeneration.request();
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (regeneration.state().etat().equals("EN_COURS")) {
            if (System.nanoTime() > limite) {
                fail("La régénération n'est pas terminée");
            }
            sleep(5);
        }
        return regeneration.state();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrompu");
        }
    }

    private int identifiantDe(String fichier, String titre) {
        return google.tabsOf(fichier).stream().filter(onglet -> onglet.title().equals(titre)).map(SheetTab::id).findFirst().orElseThrow();
    }

    private List<List<Object>> ongletGenere(String fichier) {
        return google.cellsOf(fichier, identifiantDe(fichier, ONGLET_GENERE));
    }

    private static List<Object> colonne(List<List<Object>> cellules, int colonne) {
        return cellules.stream().map(ligne -> ligne.get(colonne)).toList();
    }

    @Test
    void ca85_laRegenerationCreeLOngletGenereAvecLesRecusDeLAnneeTriesParDateEtOrdreDeValidationSansTitreDeMoisNiTotal() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        String douzeMars = valider("2026-03-12", "Hydro Québec", "Électricité");
        String cinqMarsPremier = valider("2026-03-05", "Quincaillerie A", "Clous");
        String cinqMarsSecond = valider("2026-03-05", "Quincaillerie B", "Vis");
        valider("2025-12-30", "Fournisseur 2025", "Hors année");

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).isEqualTo("REUSSIE");
        assertThat(google.tabsOf(FICHIER_2026)).extracting(SheetTab::title).as("l'onglet temporaire est retiré").containsExactly(ONGLET_GENERE);
        List<List<Object>> cellules = ongletGenere(FICHIER_2026);
        assertThat(cellules).as("en-têtes puis trois lignes, aucune ligne vide ni titre de mois ni total").hasSize(4);
        assertThat(colonne(cellules, 1)).containsExactly("BOUTIQUE", "Quincaillerie A", "Quincaillerie B", "Hydro Québec");
        assertThat(colonne(cellules, 10)).containsExactly("ID", cinqMarsPremier, cinqMarsSecond, douzeMars);
        assertThat(colonne(cellules, 0)).containsExactly("DATE", LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 12));
        assertThat(cellules.stream().skip(1)).allSatisfy(ligne -> assertThat(ligne).as("onze colonnes").hasSize(11));
    }

    @Test
    void ca85_deuxRecusDeMemeDateSontEnOrdreDeValidationEtNonDInsertionEnBase() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        String insereEnPremier = valider("2026-03-05", "Inséré en premier", "A");
        horloge.avancer(Duration.ofMinutes(-30));
        String insereEnSecond = valider("2026-03-05", "Inséré en second, validé plus tôt", "B");

        regenerer();

        assertThat(colonne(ongletGenere(FICHIER_2026), 10)).as("l'ordre de validation, pas celui de l'insertion").containsExactly("ID", insereEnSecond, insereEnPremier);
    }

    @Test
    void ca86_lOngletGenereAEnTetesFixesValeursTypeesEtIdentifiantEnTexte() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", "2026-06-16");
        valeurs.put("fournisseur", "Garage Iberville");
        valeurs.put("montantAvantTaxesCents", 61827);
        valeurs.put("tpsCents", 3091);
        valeurs.put("tvqCents", 6167);
        valeurs.put("montantTotalCents", 71085);
        Reponse reponse = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png());
        assertThat(reponse.statut()).isEqualTo(201);

        regenerer();

        List<List<Object>> cellules = ongletGenere(FICHIER_2026);
        assertThat(cellules.getFirst()).containsExactly("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO", "ID");
        List<Object> ligne = cellules.get(1);
        assertThat(ligne.get(0)).as("DATE, vraie date").isEqualTo(LocalDate.of(2026, 6, 16));
        assertThat(ligne.get(1)).isEqualTo("Garage Iberville");
        assertThat(ligne.get(2)).as("OBJET absent : cellule vide").isNull();
        assertThat(ligne.get(3)).isEqualTo("Fournitures");
        assertThat(ligne.subList(4, 8)).as("les montants sont des nombres, en dollars").usingElementComparator((a, b) -> ((BigDecimal) a).compareTo((BigDecimal) b))
                .containsExactly(new BigDecimal("618.27"), new BigDecimal("30.91"), new BigDecimal("61.67"), new BigDecimal("710.85"));
        assertThat(ligne.get(10)).as("l'identifiant du reçu, en texte").isEqualTo(reponse.json().get("id"));
        assertThat(ligne).as("aucune formule").noneMatch(InMemoryGoogle.Formula.class::isInstance);
    }

    private static final int COMPTES = 7;
    private static final int BUDGET = 3;

    /** Les onglets de l'utilisateur : des lignes manuelles, des formules, un total, et une formule d'un autre onglet qui lit l'onglet généré. */
    private void semerLesOngletsDeLUtilisateur(String fichier) {
        google.seedTab(fichier, COMPTES, "Comptes 2026", List.of(
                List.of("DATE", "BOUTIQUE", "MONTANT TOTAL"),
                List.of("mars", "", ""),
                List.of("5 mars 2026", "Saisi à la main", new BigDecimal("12.00")),
                List.of("", "TOTAL", new InMemoryGoogle.Formula("=SUM(C3:C3)"))));
        google.seedTab(fichier, BUDGET, "Budget", List.of(List.of("Dépenses", new InMemoryGoogle.Formula("='Reçus générés'!H2"))));
    }

    @Test
    void ca31_seulLOngletGenereDuFichierDeLAnneeRecoitDesEcrituresEtAucunAutreFichierNEstOuvert() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        semerLesOngletsDeLUtilisateur(FICHIER_2026);
        google.seedFile(FICHIER_2025);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        valider("2025-03-05", "Fournisseur 2025", "Autre année");

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(google.requests()).extracting(InMemoryGoogle.Request::fileId).as("aucun autre fichier n'est ouvert").containsOnly(FICHIER_2026);
        List<InMemoryGoogle.Request> ecritures = google.requests().stream().filter(requete -> !requete.operation().equals("tabs")).toList();
        assertThat(ecritures).isNotEmpty();
        assertThat(ecritures).flatExtracting(InMemoryGoogle.Request::sheetIds).as("ni « Comptes 2026 » ni « Budget » ne reçoivent d'écriture").doesNotContain(COMPTES, BUDGET);
        assertThat(google.tabsOf(FICHIER_2026)).extracting(SheetTab::title).containsExactlyInAnyOrder("Comptes 2026", "Budget", ONGLET_GENERE);
    }

    @Test
    void ca87_leContenuDesOngletsDeLUtilisateurEstIdentiqueAvantEtApresLaRegeneration() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        semerLesOngletsDeLUtilisateur(FICHIER_2026);
        List<List<Object>> comptesAvant = google.cellsOf(FICHIER_2026, COMPTES);
        List<List<Object>> budgetAvant = google.cellsOf(FICHIER_2026, BUDGET);
        valider("2026-03-05", "Quincaillerie A", "Clous");

        regenerer();

        assertThat(google.cellsOf(FICHIER_2026, COMPTES)).isEqualTo(comptesAvant);
        assertThat(google.cellsOf(FICHIER_2026, BUDGET)).isEqualTo(budgetAvant);
        assertThat(google.requests().stream().filter(requete -> !requete.operation().equals("tabs"))).flatExtracting(InMemoryGoogle.Request::sheetIds)
                .doesNotContain(COMPTES, BUDGET);
    }

    @Test
    void ca32_uneAnneeSansFichierEstSignaleeSansEchecEtLesAutresAnneesSontEcrites() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        String recu2024 = valider("2024-05-02", "Fournisseur 2024", "Sans fichier");

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).as("pas d'échec").isEqualTo("REUSSIE");
        assertThat(etat.motif()).isNull();
        assertThat(etat.anneesNonConfigurees()).containsExactly(2024);
        assertThat(etat.recusEcrits()).isEqualTo(1);
        assertThat(colonne(ongletGenere(FICHIER_2026), 10)).doesNotContain(recu2024);
        assertThat(google.requests()).extracting(InMemoryGoogle.Request::fileId).containsOnly(FICHIER_2026);
        assertThat(google.depositCalls()).as("une régénération ne dépose aucune photo (R-45)").isZero();
    }

    /** Les demandes d'écriture reçues pour ce fichier, dans l'ordre : opération et onglets visés. */
    private List<String> ecritures(String fichier) {
        return google.requests().stream().filter(requete -> requete.fileId().equals(fichier) && !requete.operation().equals("tabs"))
                .map(requete -> requete.operation() + requete.sheetIds()).toList();
    }

    @Test
    void ca88_unOngletTemporaireEstCreeEtRempliEnEntierAvantToutChangementDeLOngletGenereEtIlNExistePlusALaFin() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        regenerer();
        int genere = identifiantDe(FICHIER_2026, ONGLET_GENERE);
        List<List<Object>> avant = google.cellsOf(FICHIER_2026, genere);
        valider("2026-03-06", "Quincaillerie B", "Vis");
        google.reset();
        google.seedTab(FICHIER_2026, genere, ONGLET_GENERE, avant);
        List<List<List<Object>>> instantanes = new java.util.concurrent.CopyOnWriteArrayList<>();
        List<String> titresALEcritureTemporaire = new java.util.concurrent.CopyOnWriteArrayList<>();
        google.beforeEachSheetRequest(() -> {
            String operation = google.requests().getLast().operation();
            if (operation.equals("fill")) {
                titresALEcritureTemporaire.addAll(google.tabsOf(FICHIER_2026).stream().map(SheetTab::title).toList());
            }
            if (!operation.equals("replace") && !operation.equals("deleteTab")) {
                instantanes.add(google.cellsOf(FICHIER_2026, genere));
            }
        });

        regenerer();

        List<String> ordre = google.requests().stream().map(InMemoryGoogle.Request::operation).toList();
        assertThat(ordre).containsExactly("tabs", "addTab", "fill", "replace", "deleteTab");
        assertThat(titresALEcritureTemporaire).as("l'onglet temporaire existe, sous le titre configuré, quand il est rempli").contains(ONGLET_GENERE + " (temporaire)");
        assertThat(instantanes).as("avant le remplacement, l'onglet généré est celui d'avant").hasSize(3).allSatisfy(instantane -> assertThat(instantane).isEqualTo(avant));
        assertThat(google.tabsOf(FICHIER_2026)).extracting(SheetTab::title).as("l'onglet temporaire n'existe plus").containsExactly(ONGLET_GENERE);
        assertThat(ongletGenere(FICHIER_2026)).hasSize(3);
    }

    @Test
    void ca89_unePanneAuMilieuDeLEcritureTemporaireLaisseLOngletGenereIntactEtRetireLOngletTemporaire() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        RegenerationStateDTO reussie = regenerer();
        List<List<Object>> avant = ongletGenere(FICHIER_2026);
        valider("2026-03-06", "Quincaillerie B", "Vis");
        horloge.avancer(Duration.ofHours(1));
        google.failNext("fill", new TransientGoogleException("HTTP_503"));

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).isEqualTo("ECHEC");
        assertThat(etat.motif()).isEqualTo("GOOGLE_INDISPONIBLE");
        assertThat(etat.derniereReussiteLe()).as("la dernière réussite est inchangée").isEqualTo(reussie.derniereReussiteLe());
        assertThat(etat.dernierEchecLe()).isAfter(reussie.derniereReussiteLe());
        assertThat(ongletGenere(FICHIER_2026)).isEqualTo(avant);
        assertThat(google.tabsOf(FICHIER_2026)).extracting(SheetTab::title).containsExactly(ONGLET_GENERE);
    }

    @Test
    void ca89_unePanneAuMomentDuRemplacementLaisseLOngletGenereIntactEtRetireLOngletTemporaire() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        RegenerationStateDTO reussie = regenerer();
        List<List<Object>> avant = ongletGenere(FICHIER_2026);
        valider("2026-03-06", "Quincaillerie B", "Vis");
        google.failNext("replace", new TransientGoogleException("HTTP_429"));

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).isEqualTo("ECHEC");
        assertThat(etat.motif()).isEqualTo("GOOGLE_INDISPONIBLE");
        assertThat(etat.derniereReussiteLe()).isEqualTo(reussie.derniereReussiteLe());
        assertThat(ongletGenere(FICHIER_2026)).isEqualTo(avant);
        assertThat(google.tabsOf(FICHIER_2026)).extracting(SheetTab::title).containsExactly(ONGLET_GENERE);
    }

    @Test
    void ca89_uneErreurImprevueDuRetraitDeLOngletTemporaireNeMasquePasLEchecDOrigine() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        google.failNext("fill", new BlockedGoogleException(MotifBlocage.AUTORISATION_GOOGLE));
        google.failNext("deleteTab", new IllegalStateException("base indisponible"));

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).isEqualTo("ECHEC");
        assertThat(etat.motif()).as("le motif d'origine, non celui de l'erreur du retrait").isEqualTo("AUTORISATION_GOOGLE");
    }

    @Test
    void ca89_unOngletTemporaireQuiNePeutPasEtreRetireLeEstAuDebutDeLaRegenerationSuivante() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        google.failNext("fill", new TransientGoogleException("HTTP_503"));
        google.failNext("deleteTab", new TransientGoogleException("HTTP_503"));

        assertThat(regenerer().etat()).isEqualTo("ECHEC");

        assertThat(google.tabsOf(FICHIER_2026)).extracting(SheetTab::title).containsExactly(ONGLET_GENERE + " (temporaire)");

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(google.tabsOf(FICHIER_2026)).extracting(SheetTab::title).containsExactly(ONGLET_GENERE);
        assertThat(ongletGenere(FICHIER_2026)).hasSize(2);
    }

    @Test
    void ca90_lOngletGenereGardeLaMemeIdentiteDUneRegenerationALAutre() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        regenerer();
        int identifiant = identifiantDe(FICHIER_2026, ONGLET_GENERE);
        valider("2026-03-06", "Quincaillerie B", "Vis");

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(identifiantDe(FICHIER_2026, ONGLET_GENERE)).as("ni supprimé ni recréé").isEqualTo(identifiant);
        assertThat(ongletGenere(FICHIER_2026)).hasSize(3);
        assertThat(google.requests()).filteredOn(requete -> requete.operation().equals("deleteTab")).flatExtracting(InMemoryGoogle.Request::sheetIds)
                .doesNotContain(identifiant);
    }

    @Test
    void ca96_unOngletGenereModifieALaMainEstReecritEnEntierSansToucherLaBase() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        valider("2026-03-06", "Quincaillerie B", "Vis");
        regenerer();
        int identifiant = identifiantDe(FICHIER_2026, ONGLET_GENERE);
        List<List<Object>> attendu = google.cellsOf(FICHIER_2026, identifiant);
        List<Map<String, Object>> baseAvant = base.queryForList("select * from recu order by id");
        google.editCell(FICHIER_2026, identifiant, 1, 1, "Modifié à la main");
        google.editCell(FICHIER_2026, identifiant, 5, 1, "Ligne ajoutée à la main");
        google.editCell(FICHIER_2026, identifiant, 0, 12, "Colonne ajoutée à la main");
        google.editCell(FICHIER_2026, identifiant, 2, 5, new InMemoryGoogle.Formula("=1+1"));

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(google.cellsOf(FICHIER_2026, identifiant)).isEqualTo(attendu);
        assertThat(base.queryForList("select * from recu order by id")).isEqualTo(baseAvant);
    }

    @Test
    void ca96_unOngletGenereSupprimeEstRecree() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        regenerer();
        List<List<Object>> attendu = ongletGenere(FICHIER_2026);
        google.removeTab(FICHIER_2026, identifiantDe(FICHIER_2026, ONGLET_GENERE));

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(ongletGenere(FICHIER_2026)).isEqualTo(attendu);
        assertThat(google.tabsOf(FICHIER_2026)).hasSize(1);
    }

    @Test
    void ca96_unOngletGenereRenommeReprendSonNomConfigure() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        regenerer();
        int identifiant = identifiantDe(FICHIER_2026, ONGLET_GENERE);
        google.renameTab(FICHIER_2026, identifiant, "Mon nom à moi");

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(google.tabsOf(FICHIER_2026)).containsExactly(new SheetTab(identifiant, ONGLET_GENERE));
    }

    @Test
    void ca32_uneAnneeAjouteeALaConfigurationLeRecuApparaitDansLOngletDeCetteAnneeALaRegenerationSuivante() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        google.seedFile("fichier-essai-2024");
        String recu2024 = valider("2024-05-02", "Fournisseur 2024", "Sans fichier");
        regenerer();

        doReturn(Map.of(2026, FICHIER_2026, 2024, "fichier-essai-2024")).when(rules).files();
        RegenerationStateDTO apres = regenerer();

        assertThat(apres.anneesNonConfigurees()).isEmpty();
        assertThat(colonne(ongletGenere("fichier-essai-2024"), 10)).containsExactly("ID", recu2024);
    }

    @Test
    void r41_unOngletDuNomDeLOngletGenereCreeParLUtilisateurNEstJamaisToucheEtLaRegenerationEchoue() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedTab(FICHIER_2026, COMPTES, ONGLET_GENERE, List.of(List.of("à moi", "pas à l'application")));
        List<List<Object>> avant = google.cellsOf(FICHIER_2026, COMPTES);
        valider("2026-03-05", "Quincaillerie A", "Clous");

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).isEqualTo("ECHEC");
        assertThat(etat.motif()).isEqualTo("ONGLET_NON_RECONNU");
        assertThat(etat.anneeEnCause()).isEqualTo(2026);
        assertThat(google.cellsOf(FICHIER_2026, COMPTES)).isEqualTo(avant);
        assertThat(ecritures(FICHIER_2026)).as("aucune écriture").isEmpty();
    }

    @Test
    void r46_lEchecDUnFichierArreteLaRegenerationEtLesOngletsDejaRemplacesRestentRemplaces() {
        doReturn(Map.of(2025, FICHIER_2025, 2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2025);
        String recu2025 = valider("2025-05-02", "Fournisseur 2025", "Avant");
        valider("2026-03-05", "Quincaillerie A", "Clous");

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).isEqualTo("ECHEC");
        assertThat(etat.motif()).as("le fichier de 2026 est absent du Sheet").isEqualTo("FICHIER_INACCESSIBLE");
        assertThat(etat.anneeEnCause()).isEqualTo(2026);
        assertThat(colonne(ongletGenere(FICHIER_2025), 10)).containsExactly("ID", recu2025);
    }

    @Test
    void r42_uneAutorisationAbsenteDonneLeMotifAutorisationGoogleSansAnneeEnCause() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        google.failNext("tabs:any", new BlockedGoogleException(MotifBlocage.AUTORISATION_GOOGLE));

        RegenerationStateDTO etat = regenerer();

        assertThat(etat.etat()).isEqualTo("ECHEC");
        assertThat(etat.motif()).isEqualTo("AUTORISATION_GOOGLE");
        assertThat(etat.anneeEnCause()).isNull();
    }

    /** Les cellules mémorisées de l'onglet de l'année : reçu, colonne, contenu. */
    private List<Map<String, Object>> memorise(int annee) {
        return base.queryForList("select recu_id, colonne, contenu from onglet_genere_cellule where annee = ? order by recu_id, colonne", annee);
    }

    private String etatDuContenu(int annee) {
        return base.queryForObject("select etat_contenu from onglet_genere where annee = ?", String.class, annee);
    }

    @Test
    void cs16_desValeursQuiCommencentParUnSigneDeFormuleSontEcritesCommeTextesLitterauxDansLOngletTemporaireCommeDansLOngletGenere() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        String formule = "=IMPORTXML(\"https://exemple.invalid\",\"//a\")";
        String id1 = valider("2026-03-05", formule, "+1+1");
        String id2 = valider("2026-03-06", "Fournisseur B", "@SUM(A1)");
        String id3 = valider("2026-03-07", "Fournisseur C", "-2");
        List<List<List<Object>>> ongletTemporaireAuRemplacement = new java.util.concurrent.CopyOnWriteArrayList<>();
        google.beforeEachSheetRequest(() -> {
            InMemoryGoogle.Request demande = google.requests().getLast();
            if (demande.operation().equals("replace")) {
                ongletTemporaireAuRemplacement.add(google.cellsOf(FICHIER_2026, demande.sheetIds().getFirst()));
            }
        });

        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(ongletTemporaireAuRemplacement).hasSize(1);
        for (List<List<Object>> cellules : List.of(ongletTemporaireAuRemplacement.getFirst(), ongletGenere(FICHIER_2026))) {
            assertThat(colonne(cellules, 1)).containsExactly("BOUTIQUE", formule, "Fournisseur B", "Fournisseur C");
            assertThat(colonne(cellules, 2)).containsExactly("OBJET", "+1+1", "@SUM(A1)", "-2");
            assertThat(colonne(cellules, 10)).containsExactly("ID", id1, id2, id3);
            assertThat(cellules.stream().flatMap(List::stream)).as("aucune cellule n'est une formule").noneMatch(InMemoryGoogle.Formula.class::isInstance);
        }
    }

    @Test
    void r53_aLaReussiteLeContenuEcritEstMemoriseCelluleParCelluleSansNumeroDInscription() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("date", "2026-06-16");
        valeurs.put("fournisseur", "Garage Iberville");
        valeurs.put("montantAvantTaxesCents", 61827);
        valeurs.put("tpsCents", 3091);
        valeurs.put("tvqCents", 6167);
        valeurs.put("montantTotalCents", 71085);
        valeurs.put("numeroInscriptionTps", "123456789RT0001");
        valeurs.put("numeroInscriptionTvq", "1234567890TQ0001");
        String id = (String) RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png()).json().get("id");

        regenerer();

        Map<Object, Object> contenu = new java.util.TreeMap<>();
        memorise(2026).forEach(cellule -> {
            assertThat(cellule.get("recu_id")).isEqualTo(id);
            contenu.put(cellule.get("colonne"), cellule.get("contenu"));
        });
        assertThat(contenu).hasSize(10);
        assertThat(contenu.get(0)).isEqualTo("2026-06-16");
        assertThat(contenu.get(1)).isEqualTo("Garage Iberville");
        assertThat(contenu.get(2)).as("cellule vide").isNull();
        assertThat(contenu.get(3)).isEqualTo("Fournitures");
        assertThat(contenu.get(4)).isEqualTo("618.27");
        assertThat(contenu.get(5)).isEqualTo("30.91");
        assertThat(contenu.get(6)).isEqualTo("61.67");
        assertThat(contenu.get(7)).isEqualTo("710.85");
        assertThat(String.valueOf(contenu.values())).as("aucun numéro d'inscription").doesNotContain("RT0001").doesNotContain("TQ0001");
        assertThat(etatDuContenu(2026)).isEqualTo("CERTAIN");
    }

    @Test
    void r53_leContenuMemoriseRemplaceEntierementLePrecedentDuMemeOnglet() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        regenerer();
        assertThat(memorise(2026)).hasSize(10);
        String second = valider("2026-03-06", "Quincaillerie B", "Vis");

        regenerer();

        assertThat(memorise(2026)).hasSize(20).extracting(cellule -> cellule.get("recu_id")).contains(second);
    }

    @Test
    void r53_avantLeRemplacementLOngletEstNoteEnCoursEtUnEchecLaisseLeMemoriseIntactMaisIncertain() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        regenerer();
        List<Map<String, Object>> memoriseAvant = memorise(2026);
        valider("2026-03-06", "Quincaillerie B", "Vis");
        List<String> noteAuRemplacement = new java.util.concurrent.CopyOnWriteArrayList<>();
        google.beforeEachSheetRequest(() -> {
            if (google.requests().getLast().operation().equals("replace")) {
                noteAuRemplacement.add(etatDuContenu(2026));
            }
        });
        google.failNext("replace", new TransientGoogleException("REPONSE_PERDUE"));

        assertThat(regenerer().etat()).isEqualTo("ECHEC");

        assertThat(noteAuRemplacement).as("noté en cours avant la demande de remplacement").containsExactly("INCERTAIN");
        assertThat(memorise(2026)).as("le contenu mémorisé n'est remplacé qu'à la réussite").isEqualTo(memoriseAvant);
        assertThat(etatDuContenu(2026)).as("l'issue du remplacement est inconnue").isEqualTo("INCERTAIN");

        google.beforeEachSheetRequest(() -> { });
        assertThat(regenerer().etat()).isEqualTo("REUSSIE");

        assertThat(etatDuContenu(2026)).isEqualTo("CERTAIN");
        assertThat(memorise(2026)).hasSize(20);
    }

    @Test
    void r53_rienNEstMemorisePourUnOngletJamaisEcrit() {
        doReturn(Map.of(2026, FICHIER_2026)).when(rules).files();
        google.seedFile(FICHIER_2026);
        valider("2026-03-05", "Quincaillerie A", "Clous");
        google.failNext("fill", new TransientGoogleException("HTTP_503"));

        assertThat(regenerer().etat()).isEqualTo("ECHEC");

        assertThat(memorise(2026)).isEmpty();
        assertThat(etatDuContenu(2026)).isEqualTo("INCERTAIN");
    }
}
