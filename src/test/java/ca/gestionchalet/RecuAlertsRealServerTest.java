package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Les quatre reçus réels, par leurs seules valeurs du PRD (images générées) : validés de bout en bout, jamais bloqués par une alerte. */
@RealServerTest
class RecuAlertsRealServerTest {

    private static final String NUMERO_TPS = "123456789RT0001";
    private static final String NUMERO_TVQ = "1234567890TQ0001";

    @LocalServerPort
    int port;

    private TestClient client;

    @BeforeEach
    void connecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private static Map<String, Object> saisie(String fournisseur, Long avantTaxes, Long tps, Long tvq, long total, boolean numeros) {
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("date", "2026-06-16");
        valeurs.put("fournisseur", fournisseur);
        valeurs.put("typeDepense", "Fournitures");
        valeurs.put("montantAvantTaxesCents", avantTaxes);
        valeurs.put("tpsCents", tps);
        valeurs.put("tvqCents", tvq);
        valeurs.put("montantTotalCents", total);
        if (numeros) {
            valeurs.put("numeroInscriptionTps", NUMERO_TPS);
            valeurs.put("numeroInscriptionTvq", NUMERO_TVQ);
        }
        return valeurs;
    }

    private Reponse valider(Map<String, Object> corps) {
        return RecuRequests.valider(client, corps, RecuRequests.png());
    }

    private Reponse valider(String fournisseur, Long avantTaxes, Long tps, Long tvq, long total, boolean numeros) {
        return valider(RecuRequests.corps(saisie(fournisseur, avantTaxes, tps, tvq, total, numeros)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> valeursDe(Reponse reponse) {
        return (Map<String, Object>) reponse.json().get("valeurs");
    }

    @Test
    void ca09_leRecuStmNaQueSonTotalEtAucuneAlerte() {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", "STM");

        Reponse reponse = valider(RecuRequests.corps(valeurs));

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(valeursDe(reponse)).containsEntry("montantTotalCents", 3500).containsEntry("montantAvantTaxesCents", null)
                .containsEntry("tpsCents", null).containsEntry("tvqCents", null);
        assertThat(reponse.json()).containsEntry("alertes", List.of());
    }

    @Test
    void ca10_leRecuUnimageNAAucuneAlerte() {
        Reponse reponse = valider("Unimage", 26500L, 1325L, 2643L, 30468L, true);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(valeursDe(reponse)).containsEntry("montantAvantTaxesCents", 26500).containsEntry("tpsCents", 1325)
                .containsEntry("tvqCents", 2643).containsEntry("montantTotalCents", 30468);
        assertThat(reponse.json()).containsEntry("alertes", List.of());
    }

    @Test
    void ca11_leRecuGarageIbervilleADeuxAlertesDansLOrdreDuTableau() {
        Reponse reponse = valider("Garage Iberville", 61827L, 3091L, 6167L, 71085L, false);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("alertes", List.of(
                Map.of("code", "NUMEROS_INSCRIPTION_MANQUANTS", "message", "Numéros d'inscription manquants : TPS, TVQ"),
                Map.of("code", "PIECE_A_VERIFIER", "message", "Pièce à vérifier : nom de l'acheteur, description, conditions de paiement")));
    }

    @Test
    void ca12_leRecuFamiliprixGardeSonFournisseurSansAlerte() {
        Reponse reponse = valider("Familiprix", 1379L, 69L, 138L, 1586L, false);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(valeursDe(reponse)).containsEntry("fournisseur", "Familiprix");
        assertThat(reponse.json()).containsEntry("alertes", List.of());
    }

    @Test
    void ca25_uneLigneHydroQuebecEstCreeeAvecSonEcart() {
        Reponse reponse = valider("Hydro Québec", 11894L, 595L, 1187L, 14246L, true);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("alertes",
                List.of(Map.of("code", "ECART_CALCUL", "message", "Écart de calcul : 5,70 $", "ecartCents", 570)));
    }

    @Test
    void ca26_lesTaxesVidesRestentNullDansLeRecuEnregistre() {
        Reponse reponse = valider("Taxes vides", 10000L, null, null, 11500L, true);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(valeursDe(reponse)).containsEntry("tpsCents", null).containsEntry("tvqCents", null);
        assertThat(reponse.json()).containsEntry("alertes",
                List.of(Map.of("code", "ECART_CALCUL", "message", "Écart de calcul : 15,00 $", "ecartCents", 1500)));
    }

    @Test
    void ca29_unTotalCorrigeA40DollarsGardeLAlerteDuTotalLuDe120() {
        Map<String, Object> corps = RecuRequests.corps(saisie("Total corrigé", null, null, null, 4000L, false));
        corps.put("montantTotalLuCents", 12000);

        Reponse reponse = valider(corps);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("alertes",
                List.of(Map.of("code", "NUMEROS_INSCRIPTION_MANQUANTS", "message", "Numéros d'inscription manquants : TPS, TVQ")));
    }

    @Test
    void ca18_unRenvoiRendLesAlertesEnregistrees() {
        Map<String, Object> corps = RecuRequests.corps(saisie("Renvoi alerté", 61827L, 3091L, 6167L, 71085L, false));
        Map<String, Object> premier = valider(corps).json();

        Reponse renvoi = valider(corps);

        assertThat(renvoi.statut()).isEqualTo(200);
        assertThat(renvoi.json().get("alertes")).isEqualTo(premier.get("alertes"));
        assertThat((List<?>) renvoi.json().get("alertes")).hasSize(2);
    }

    @Test
    void cs17_uneAlerteEnvoyeeParLeClientEstSansEffet() {
        Map<String, Object> valeurs = saisie("Alerte du client", null, null, null, 3500L, false);
        valeurs.put("alertes", List.of(Map.of("code", "PIECE_A_VERIFIER", "message", "fausse")));
        Map<String, Object> corps = RecuRequests.corps(valeurs);
        corps.put("alertes", List.of(Map.of("code", "PIECE_A_VERIFIER", "message", "fausse")));

        assertThat(valider(corps).json()).containsEntry("alertes", List.of());
    }

    private static final Map<String, Object> RAPPORT = Map.of("code", "RAPPORT_TVQ_TPS", "message", "Rapport TVQ / TPS inhabituel : TVQ 5,95 $ pour une TPS de 11,87 $");
    private static final Map<String, Object> AU_DELA = Map.of("code", "TPS_AU_DELA_DU_TAUX",
            "message", "TPS supérieure à 5 % du montant avant taxes : 11,87 $ pour 118,94 $");

    @Test
    @SuppressWarnings("unchecked")
    void ca111_leControleRendLesDeuxAlertesSansRienEnregistrerPuisLaValidationCreeLeRecuAvecElles() {
        Map<String, Object> valeurs = saisie("Taxes inversées", 11894L, 1187L, 595L, 13676L, true);
        Map<String, Object> corpsControle = new LinkedHashMap<>();
        corpsControle.put("valeurs", valeurs);

        Reponse controle = client.appelAvecJeton("POST", "/api/recus/controles", new tools.jackson.databind.json.JsonMapper().writeValueAsString(corpsControle));

        assertThat(controle.statut()).isEqualTo(200);
        assertThat(controle.json()).isEqualTo(Map.of("alertes", List.of(RAPPORT, AU_DELA)));

        Reponse validation = valider(RecuRequests.corps(valeurs));

        assertThat(validation.statut()).isEqualTo(201);
        assertThat(validation.json()).containsEntry("alertes", List.of(RAPPORT, AU_DELA));
        assertThat(valeursDe(validation)).containsEntry("tpsCents", 1187).containsEntry("tvqCents", 595);
    }
}
