package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.ByteArrayInputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

/** {@code POST /api/recus/controles} : les alertes avant la validation, sans rien enregistrer, et ses entrées hostiles. */
@RealServerTest
@ExtendWith(OutputCaptureExtension.class)
class RecuCheckRealServerTest {

    private static final String CHEMIN = "/api/recus/controles";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, String> TROP_VOLUMINEUX = Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE");
    private static final String VALEURS_VALIDES = "\"valeurs\":{\"date\":\"2026-06-16\",\"fournisseur\":\"F\",\"typeDepense\":\"Fournitures\",\"montantTotalCents\":2500}";
    private static final Map<String, String> DOUBLON = Map.of("code", "DOUBLON_POSSIBLE", "message", "Doublon possible");

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    private TestClient client;

    @BeforeEach
    void connecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private static Map<String, Object> saisie(String fournisseur, String date, long total) {
        Map<String, Object> valeurs = RecuRequests.valeurs();
        valeurs.put("fournisseur", fournisseur);
        valeurs.put("date", date);
        valeurs.put("montantTotalCents", total);
        return valeurs;
    }

    private Reponse controler(Map<String, Object> valeurs) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("valeurs", valeurs);
        return client.appelAvecJeton("POST", CHEMIN, JSON.writeValueAsString(corps));
    }

    private long lignes(String table) {
        return base.queryForObject("select count(*) from " + table, Long.class);
    }

    private String unique(String debut) {
        return debut + " " + UUID.randomUUID();
    }

    private void enregistrer(Map<String, Object> valeurs) {
        assertThat(RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png()).statut()).isEqualTo(201);
    }

    @Test
    void ca27_leControleSignaleLeDoublonSansRienEnregistrerPuisLaValidationCreeLeRecuAvecLAlerte() {
        enregistrer(saisie("Café du Lac", "2026-06-16", 2500));
        long recus = lignes("recu");
        long alertes = lignes("recu_alerte");

        Reponse controle = controler(saisie("CAFE DU LAC", "2026-06-16", 2500));

        assertThat(controle.statut()).isEqualTo(200);
        assertThat(controle.json()).isEqualTo(Map.of("alertes", List.of(DOUBLON)));
        assertThat(lignes("recu")).isEqualTo(recus);
        assertThat(lignes("recu_alerte")).isEqualTo(alertes);

        Reponse validation = RecuRequests.valider(client, RecuRequests.corps(saisie("CAFE DU LAC", "2026-06-16", 2500)), RecuRequests.png());

        assertThat(validation.statut()).isEqualTo(201);
        assertThat(validation.json()).containsEntry("alertes", List.of(DOUBLON));
    }

    @Test
    void ca27_laCasseLesAccentsEtLesEspacesEnBordureNeComptentPasMaisLesAutresValeursSi() {
        String nom = unique("Épicerie Noël");
        enregistrer(saisie(nom, "2026-06-16", 2500));

        assertThat(controler(saisie("  " + nom.toUpperCase().replace("É", "E").replace("Ë", "E") + " ", "2026-06-16", 2500)).json().get("alertes")).isEqualTo(List.of(DOUBLON));
        assertThat(controler(saisie(nom + " bis", "2026-06-16", 2500)).json().get("alertes")).isEqualTo(List.of());
        assertThat(controler(saisie(nom, "2026-06-17", 2500)).json().get("alertes")).isEqualTo(List.of());
        assertThat(controler(saisie(nom, "2026-06-16", 2501)).json().get("alertes")).isEqualTo(List.of());
    }

    @Test
    void ca18_unRenvoiNeSePrendPasPourSonPropreDoublon() {
        Map<String, Object> corps = RecuRequests.corps(saisie(unique("Renvoi"), "2026-06-16", 2500));
        assertThat(RecuRequests.valider(client, corps, RecuRequests.png()).statut()).isEqualTo(201);

        Reponse renvoi = RecuRequests.valider(client, corps, RecuRequests.png());

        assertThat(renvoi.statut()).isEqualTo(200);
        assertThat(renvoi.json()).containsEntry("alertes", List.of());
    }

    @Test
    void ca27_recuIdEcarteCeRecuDeLaComparaison() {
        String nom = unique("Reçu comparé");
        Map<String, Object> valeurs = saisie(nom, "2026-06-16", 2500);
        Map<String, Object> enregistre = RecuRequests.valider(client, RecuRequests.corps(valeurs), RecuRequests.png()).json();
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("valeurs", valeurs);
        corps.put("recuId", enregistre.get("id"));

        assertThat(client.appelAvecJeton("POST", CHEMIN, JSON.writeValueAsString(corps)).json()).containsEntry("alertes", List.of());

        corps.put("recuId", UUID.randomUUID().toString());
        assertThat(client.appelAvecJeton("POST", CHEMIN, JSON.writeValueAsString(corps)).json()).containsEntry("alertes", List.of(DOUBLON));
    }

    @Test
    void ca29_leControleTientCompteDuTotalLu() {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("valeurs", saisie(unique("Total lu"), "2026-06-16", 4000));
        corps.put("montantTotalLuCents", 12000);

        assertThat(client.appelAvecJeton("POST", CHEMIN, JSON.writeValueAsString(corps)).json()).isEqualTo(Map.of("alertes",
                List.of(Map.of("code", "NUMEROS_INSCRIPTION_MANQUANTS", "message", "Numéros d'inscription manquants : TPS, TVQ"))));
    }

    @Test
    void cs17_uneAlerteEnvoyeeAuControleEstSansEffet() {
        Map<String, Object> valeurs = saisie(unique("Client"), "2026-06-16", 2500);
        valeurs.put("alertes", List.of(DOUBLON));
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("valeurs", valeurs);
        corps.put("alertes", List.of(DOUBLON));

        assertThat(client.appelAvecJeton("POST", CHEMIN, JSON.writeValueAsString(corps)).json()).containsEntry("alertes", List.of());
    }

    @Test
    void uneInjectionEnFournisseurNeTrouveAucunDoublon() {
        enregistrer(saisie(unique("Témoin injection"), "2026-06-16", 2500));

        Reponse reponse = controler(saisie("' OR 1=1--", "2026-06-16", 2500));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).containsEntry("alertes", List.of());
    }

    @Test
    void cs01_sansSessionLeControleDonne401() {
        TestClient anonyme = new TestClient(port, "source-" + UUID.randomUUID());
        Map<String, Object> corps = Map.of("valeurs", saisie("Anonyme", "2026-06-16", 2500));

        Reponse reponse = anonyme.appelAvecJeton("POST", CHEMIN, JSON.writeValueAsString(corps));

        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).containsEntry("code", "NON_AUTHENTIFIE").doesNotContainKey("alertes");
    }

    @Test
    void cs02_sansJetonLeControleDonne403() {
        Reponse reponse = client.envoyer(client.requete(CHEMIN).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"valeurs\":{}}")).build());

        assertThat(reponse.statut()).isEqualTo(403);
        assertThat(reponse.json()).containsEntry("code", "CSRF_INVALIDE");
    }

    @Test
    void cs02_unJetonFauxDonne403() {
        Reponse reponse = client.appelSansJeton("POST", CHEMIN, "{\"valeurs\":{}}", "faux");

        assertThat(reponse.statut()).isEqualTo(403);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Jeton de sécurité absent ou invalide.", "code", "CSRF_INVALIDE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded", "multipart/form-data; boundary=x"})
    void unAutreTypeDeContenuDonne415(String type) {
        Reponse reponse = client.envoyer(client.requete(CHEMIN).header("Content-Type", type).header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build());

        assertThat(reponse.statut()).isEqualTo(415);
    }

    @Test
    void cs08_unCorpsAuDelaDe64KoDonne413() {
        Reponse reponse = client.appelAvecJeton("POST", CHEMIN, "{\"valeurs\":{\"fournisseur\":\"" + "x".repeat(64 * 1024) + "\"}}");

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(TROP_VOLUMINEUX);
    }

    @Test
    void cs08_unCorpsFragmenteAuDelaDe64KoDonne413() {
        byte[] corps = ("{\"valeurs\":{\"fournisseur\":\"" + "x".repeat(64 * 1024) + "\"}}").getBytes(StandardCharsets.UTF_8);

        Reponse reponse = client.envoyer(client.requete(CHEMIN).header("Content-Type", "application/json").header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(corps))).build());

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(TROP_VOLUMINEUX);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "pas du json",
        "{\"valeurs\":{\"date\":\"2026-06-16\",\"fournisseur\":12,\"typeDepense\":\"Fournitures\",\"montantTotalCents\":2500}}",
        "{\"valeurs\":{\"date\":\"16/06/2026\",\"fournisseur\":\"F\",\"typeDepense\":\"Fournitures\",\"montantTotalCents\":2500}}",
        "{\"valeurs\":{\"date\":\"2026-06-16\",\"fournisseur\":\"F\",\"typeDepense\":\"Fournitures\",\"montantTotalCents\":25.5}}",
        "{\"valeurs\":{\"date\":\"2026-06-16\",\"fournisseur\":\"F\",\"typeDepense\":\"Fournitures\",\"montantTotalCents\":\"2500\"}}",
        "{\"montantTotalLuCents\":25.5," + VALEURS_VALIDES + "}",
        "{\"montantTotalLuCents\":\"2500\"," + VALEURS_VALIDES + "}",
        "{\"recuId\":\"pas-un-uuid\",\"valeurs\":{\"date\":\"2026-06-16\",\"fournisseur\":\"F\",\"typeDepense\":\"Fournitures\",\"montantTotalCents\":2500}}",
    })
    void uneConversionNonStricteEstRefusee(String corps) {
        assertRequeteInvalide(corps);
    }

    @Test
    void cs27_unObjetTypeEnFournisseurEstRefuse() {
        assertRequeteInvalide("{\"valeurs\":{\"date\":\"2026-06-16\",\"fournisseur\":{\"@class\":\"java.lang.Runtime\"},\"typeDepense\":\"Fournitures\",\"montantTotalCents\":2500}}");
    }

    /** Sous la limite de 64 Ko : c'est la profondeur ou la longueur du nombre qui doit être refusée, sans débordement de pile. */
    @Test
    void uneProfondeurOuUnNombreDemesuresSontRefuses() {
        assertRequeteInvalide("[".repeat(30_000));
        assertRequeteInvalide("{\"a\":".repeat(9_000));
        assertRequeteInvalide("{\"montantTotalLuCents\":" + "9".repeat(60_000) + "," + VALEURS_VALIDES + "}");
    }

    private void assertRequeteInvalide(String corps) {
        Reponse reponse = client.appelAvecJeton("POST", CHEMIN, corps);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE"));
    }

    /** U+202E (inversion du sens), U+0000, U+2028 : refusés comme à la validation. */
    @ParameterizedTest
    @ValueSource(strings = {"F\u202E", "F\u0000x", "F\u2028x"})
    void unCaractereDeFormatEstRefuse(String fournisseur) {
        Reponse reponse = controler(saisie(fournisseur, "2026-06-16", 2500));

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).containsEntry("code", "VALIDATION");
    }

    @Test
    void ca16_lesBornesDesValeursSontCellesDeLaValidation() {
        assertThat(controler(saisie("F", "2026-06-16", 100_000_000L)).statut()).isEqualTo(200);
        assertThat(controler(saisie("F", "2026-06-16", 100_000_001L)).statut()).isEqualTo(400);
        Map<String, Object> sansType = saisie("F", "2026-06-16", 2500);
        sansType.remove("typeDepense");
        assertThat(controler(sansType).statut()).isEqualTo(400);
        Map<String, Object> lu = new LinkedHashMap<>();
        lu.put("valeurs", saisie("F", "2026-06-16", 2500));
        lu.put("montantTotalLuCents", -1);
        assertThat(client.appelAvecJeton("POST", CHEMIN, JSON.writeValueAsString(lu)).statut()).isEqualTo(400);
    }

    @Test
    void aucuneValeurDuControleNeVaAuxJournaux(CapturedOutput journaux) {
        String marque = "MarqueJournal" + UUID.randomUUID();
        Map<String, Object> valeurs = saisie(marque, "2026-06-16", 2500);

        assertThat(controler(valeurs).statut()).isEqualTo(200);
        assertThat(client.appelAvecJeton("POST", CHEMIN, "{\"valeurs\":{\"date\":\"" + marque + "\"}}").statut()).isEqualTo(400);

        assertThat(journaux.getAll()).doesNotContain(marque);
    }

    @Test
    void cs09_laReponseDuControlePorteLesEnTetesDeSecurite() {
        Reponse reponse = controler(saisie(unique("En-têtes"), "2026-06-16", 2500));

        assertThat(reponse.enTete("Content-Security-Policy")).isEqualTo("default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
                + "connect-src 'self'; manifest-src 'self'; worker-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
        assertThat(reponse.enTete("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(reponse.enTete("Cache-Control")).contains("no-store");
    }
}
