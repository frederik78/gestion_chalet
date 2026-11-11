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
}
