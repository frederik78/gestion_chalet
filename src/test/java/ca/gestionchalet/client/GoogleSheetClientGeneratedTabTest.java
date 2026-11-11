package ca.gestionchalet.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.support.ApiStub;
import ca.gestionchalet.support.ApiStub.Request;
import ca.gestionchalet.support.ApiStub.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** L'adaptateur du Sheet pour l'onglet généré, contre un serveur local : le format exact des requêtes de la section 7.4 du PRD, le littéral d'écriture et le classement des pannes. */
class GoogleSheetClientGeneratedTabTest {

    private static final String FICHIER = "fichier-essai";
    private static final String ID_RECU = "22222222-2222-4222-8222-222222222222";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ApiStub sheets;
    private Response reponse = Response.json("{}");

    @BeforeEach
    void demarrer() throws IOException {
        sheets = new ApiStub();
        sheets.answer(requete -> reponse);
    }

    @AfterEach
    void arreter() {
        sheets.close();
    }

    private SheetClient client() {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrique.setReadTimeout(Duration.ofSeconds(3));
        return new GoogleSheetClient(RestClient.builder().requestFactory(fabrique).baseUrl(sheets.address()).build(), () -> Optional.of("jeton-d-essai"),
                "https://drive.exemple.invalid/fichier/{id}/voir");
    }

    private static SheetRow ligne(String fournisseur, String objet, String notes) {
        return new SheetRow(ID_RECU, LocalDate.of(2026, 6, 16), fournisseur, objet, "Entretien / Réparations", new BigDecimal("618.27"), new BigDecimal("30.91"),
                new BigDecimal("61.67"), new BigDecimal("710.85"), notes, List.of(new PhotoLink("Photo 1", "fichier-a")));
    }

    private static JsonNode corps(Request requete) {
        return JSON.readTree(new String(requete.body(), StandardCharsets.UTF_8));
    }

    private Request lot() {
        List<Request> lots = sheets.requests().stream().filter(requete -> requete.path().endsWith(":batchUpdate")).toList();
        assertThat(lots).as("un seul lot").hasSize(1);
        return lots.getFirst();
    }

    /** La demande du lot qui porte cette clé, dans l'ordre du lot. */
    private static List<JsonNode> demandes(Request lot, String type) {
        List<JsonNode> trouvees = new ArrayList<>();
        for (JsonNode demande : corps(lot).get("requests")) {
            if (demande.has(type)) {
                trouvees.add(demande.get(type));
            }
        }
        return trouvees;
    }

    @Test
    void ca31_lesOngletsDuFichierSeLisentParSonIdentifiantSansLeurContenu() {
        reponse = Response.json("{\"sheets\":[{\"properties\":{\"sheetId\":3,\"title\":\"Budget\"}},{\"properties\":{\"sheetId\":7,\"title\":\"Comptes 2026\"}}]}");

        List<SheetTab> onglets = client().tabs(FICHIER);

        assertThat(onglets).containsExactly(new SheetTab(3, "Budget"), new SheetTab(7, "Comptes 2026"));
        Request requete = sheets.requests().getFirst();
        assertThat(requete.method()).isEqualTo("GET");
        assertThat(requete.path()).isEqualTo("/v4/spreadsheets/" + FICHIER);
        assertThat(requete.query()).isEqualTo("fields=sheets.properties(sheetId,title)");
        assertThat(requete.header("Authorization")).isEqualTo("Bearer jeton-d-essai");
    }

    @Test
    void ca31_uneReponseSansListeDOngletsEstUnePanneEtUnFichierRefuseBloque() {
        reponse = Response.json("{}");
        assertThatThrownBy(() -> client().tabs(FICHIER)).isInstanceOf(TransientGoogleException.class);

        reponse = Response.status(404);
        assertThatThrownBy(() -> client().tabs(FICHIER)).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));
    }

    @Test
    void ca88_unOngletEstCreeAvecL_identifiantChoisiEtSonTitreSeulement() {
        client().addTab(FICHIER, 4242, "Reçus générés (temporaire)");

        Request lot = lot();
        assertThat(lot.path()).isEqualTo("/v4/spreadsheets/" + FICHIER + ":batchUpdate");
        assertThat(corps(lot).get("requests")).hasSize(1);
        JsonNode propriete = demandes(lot, "addSheet").getFirst().get("properties");
        assertThat(propriete.get("sheetId").asInt()).isEqualTo(4242);
        assertThat(propriete.get("title").asString()).isEqualTo("Reçus générés (temporaire)");
    }

    @Test
    void ca86_leLotRedimensionneL_ongletPuisEcritLesEnTetesEtLesLignesDansLOrdreDuPrd() {
        client().fill(FICHIER, 4242, List.of(ligne("Garage Iberville", null, "Pièce à vérifier : description")));

        Request lot = lot();
        JsonNode taille = demandes(lot, "updateSheetProperties").getFirst();
        assertThat(taille.at("/properties/sheetId").asInt()).isEqualTo(4242);
        assertThat(taille.at("/properties/gridProperties/rowCount").asInt()).isEqualTo(2);
        assertThat(taille.at("/properties/gridProperties/columnCount").asInt()).isEqualTo(11);
        assertThat(taille.get("fields").asString()).isEqualTo("gridProperties(rowCount,columnCount)");
        JsonNode ecriture = demandes(lot, "updateCells").getFirst();
        assertThat(ecriture.at("/start/sheetId").asInt()).isEqualTo(4242);
        assertThat(ecriture.at("/start/rowIndex").asInt()).isZero();
        assertThat(ecriture.at("/start/columnIndex").asInt()).isZero();
        assertThat(ecriture.get("fields").asString()).isEqualTo("userEnteredValue,userEnteredFormat,textFormatRuns");
        JsonNode lignes = ecriture.get("rows");
        assertThat(lignes).hasSize(2);
        List<String> enTetes = new ArrayList<>();
        lignes.get(0).get("values").forEach(cellule -> enTetes.add(cellule.at("/userEnteredValue/stringValue").asString()));
        assertThat(enTetes).containsExactly("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO", "ID");
        JsonNode valeurs = lignes.get(1).get("values");
        assertThat(valeurs).hasSize(11);
        assertThat(valeurs.get(0).at("/userEnteredValue/numberValue").decimalValue()).as("DATE, vraie date").isEqualByComparingTo("46189");
        assertThat(valeurs.get(0).at("/userEnteredFormat/numberFormat/type").asString()).isEqualTo("DATE");
        assertThat(valeurs.get(0).at("/userEnteredFormat/numberFormat/pattern").asString()).isEqualTo("d mmmm yyyy");
        assertThat(valeurs.get(1).at("/userEnteredValue/stringValue").asString()).isEqualTo("Garage Iberville");
        assertThat(valeurs.get(2).has("userEnteredValue")).as("OBJET absent : cellule vide").isFalse();
        assertThat(valeurs.get(3).at("/userEnteredValue/stringValue").asString()).isEqualTo("Entretien / Réparations");
        assertThat(valeurs.get(4).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("618.27");
        assertThat(valeurs.get(5).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("30.91");
        assertThat(valeurs.get(6).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("61.67");
        assertThat(valeurs.get(7).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("710.85");
        assertThat(valeurs.get(4).at("/userEnteredFormat/numberFormat/type").asString()).isEqualTo("NUMBER");
        assertThat(valeurs.get(4).at("/userEnteredFormat/numberFormat/pattern").asString()).isEqualTo("#,##0.00");
        assertThat(valeurs.get(8).at("/userEnteredValue/stringValue").asString()).isEqualTo("Pièce à vérifier : description");
        assertThat(valeurs.get(9).at("/userEnteredValue/stringValue").asString()).isEqualTo("Photo 1");
        assertThat(valeurs.get(9).at("/textFormatRuns/0/format/link/uri").asString()).isEqualTo("https://drive.exemple.invalid/fichier/fichier-a/voir");
    }

    @Test
    void ca86_r51_laColonneIdEstUnTexteLitteralGriseEnFondEtEnTexteEnTeteComprise() {
        client().fill(FICHIER, 4242, List.of(ligne("Garage Iberville", null, "")));

        JsonNode lignes = demandes(lot(), "updateCells").getFirst().get("rows");
        for (int rang = 0; rang <= 1; rang++) {
            JsonNode id = lignes.get(rang).get("values").get(10);
            assertThat(id.at("/userEnteredFormat/backgroundColor/red").decimalValue()).as("fond, ligne " + rang).isEqualByComparingTo("0.85");
            assertThat(id.at("/userEnteredFormat/textFormat/foregroundColor/red").decimalValue()).as("texte, ligne " + rang).isEqualByComparingTo("0.6");
        }
        assertThat(lignes.get(1).get("values").get(10).at("/userEnteredValue/stringValue").asString()).isEqualTo(ID_RECU);
        assertThat(lignes.get(0).get("values").get(0).at("/userEnteredFormat/textFormat/bold").asBoolean()).as("en-têtes en gras").isTrue();
        assertThat(lignes.get(1).get("values").get(1).has("userEnteredFormat")).as("aucune mise en forme hors date, montants, en-têtes et ID").isFalse();
    }

    @Test
    void cs16_unTexteQuiCommenceParUnSigneDeFormuleEstEcritCommeTexteLitteralPartout() {
        String formule = "=IMPORTXML(\"https://exemple.invalid\",\"//a\")";
        List<SheetRow> lignes = List.of(ligne(formule, "+1+1", "@SUM(A1)"), ligne("Autre", "-2", "=NOTES"));

        client().fill(FICHIER, 4242, lignes);

        assertThat(new String(lot().body(), StandardCharsets.UTF_8)).doesNotContain("formulaValue");
        JsonNode valeurs = demandes(lot(), "updateCells").getFirst().get("rows");
        assertThat(valeurs.get(1).get("values").get(1).at("/userEnteredValue/stringValue").asString()).isEqualTo(formule);
        assertThat(valeurs.get(1).get("values").get(2).at("/userEnteredValue/stringValue").asString()).isEqualTo("+1+1");
        assertThat(valeurs.get(1).get("values").get(8).at("/userEnteredValue/stringValue").asString()).isEqualTo("@SUM(A1)");
        assertThat(valeurs.get(2).get("values").get(2).at("/userEnteredValue/stringValue").asString()).isEqualTo("-2");
        assertThat(valeurs.get(2).get("values").get(8).at("/userEnteredValue/stringValue").asString()).isEqualTo("=NOTES");
        for (JsonNode cellule : valeurs.get(1).get("values")) {
            assertThat(cellule.has("userEnteredValue") ? cellule.get("userEnteredValue").propertyNames() : List.of()).doesNotContain("formulaValue");
        }
    }

    @Test
    void cs16_ni_leMaterielNiLeNomDeLOngletNeSontInterpretesUnIdentifiantHostileResteUnTexte() {
        SheetRow hostile = new SheetRow("=1+1", LocalDate.of(2026, 6, 16), "F", null, "T", null, null, null, new BigDecimal("1.00"), "", List.of());

        client().fill(FICHIER, 4242, List.of(hostile));

        JsonNode id = demandes(lot(), "updateCells").getFirst().at("/rows/1/values/10");
        assertThat(id.at("/userEnteredValue/stringValue").asString()).isEqualTo("=1+1");
        assertThat(id.at("/userEnteredValue").propertyNames()).containsExactly("stringValue");
    }

    @Test
    void ca88_ca90_leRemplacementEstUnSeulLotQuiViseLOngletCibleSansLeSupprimerNiLeRecreer() {
        client().replace(FICHIER, 4242, 77, "Reçus générés", false, 5);

        Request lot = lot();
        assertThat(demandes(lot, "addSheet")).as("l'onglet cible existe : il garde son identité").isEmpty();
        assertThat(demandes(lot, "deleteSheet")).isEmpty();
        JsonNode proprietes = demandes(lot, "updateSheetProperties").getFirst();
        assertThat(proprietes.at("/properties/sheetId").asInt()).isEqualTo(77);
        assertThat(proprietes.at("/properties/title").asString()).as("reprend son nom configuré").isEqualTo("Reçus générés");
        assertThat(proprietes.at("/properties/gridProperties/rowCount").asInt()).isEqualTo(5);
        assertThat(proprietes.at("/properties/gridProperties/columnCount").asInt()).isEqualTo(11);
        assertThat(proprietes.get("fields").asString()).isEqualTo("title,gridProperties(rowCount,columnCount)");
        JsonNode effacement = demandes(lot, "updateCells").getFirst();
        assertThat(effacement.at("/range/sheetId").asInt()).isEqualTo(77);
        assertThat(effacement.get("fields").asString()).isEqualTo("*");
        assertThat(effacement.has("rows")).isFalse();
        JsonNode copie = demandes(lot, "copyPaste").getFirst();
        assertThat(copie.at("/source/sheetId").asInt()).isEqualTo(4242);
        assertThat(copie.at("/source/endRowIndex").asInt()).isEqualTo(5);
        assertThat(copie.at("/source/endColumnIndex").asInt()).isEqualTo(11);
        assertThat(copie.at("/destination/sheetId").asInt()).isEqualTo(77);
        assertThat(copie.at("/destination/startRowIndex").asInt()).isZero();
        assertThat(copie.at("/destination/endRowIndex").asInt()).isEqualTo(5);
        assertThat(copie.at("/destination/endColumnIndex").asInt()).isEqualTo(11);
        assertThat(copie.get("pasteType").asString()).isEqualTo("PASTE_NORMAL");
        List<String> ordre = new ArrayList<>();
        corps(lot).get("requests").forEach(demande -> ordre.addAll(demande.propertyNames()));
        assertThat(ordre).containsExactly("updateSheetProperties", "updateCells", "copyPaste");
    }

    @Test
    void ca85_laPremiereRegenerationCreeLOngletCibleDansLeMemeLotAvantDeLeRemplir() {
        client().replace(FICHIER, 4242, 77, "Reçus générés", true, 2);

        Request lot = lot();
        JsonNode creation = demandes(lot, "addSheet").getFirst().get("properties");
        assertThat(creation.get("sheetId").asInt()).isEqualTo(77);
        assertThat(creation.get("title").asString()).isEqualTo("Reçus générés");
        List<String> ordre = new ArrayList<>();
        corps(lot).get("requests").forEach(demande -> ordre.addAll(demande.propertyNames()));
        assertThat(ordre).containsExactly("addSheet", "updateSheetProperties", "updateCells", "copyPaste");
    }

    @Test
    void ca88_leRetraitDeLOngletTemporaireViseCetOngletEtLuiSeul() {
        client().deleteTab(FICHIER, 4242);

        Request lot = lot();
        assertThat(corps(lot).get("requests")).hasSize(1);
        assertThat(demandes(lot, "deleteSheet").getFirst().get("sheetId").asInt()).isEqualTo(4242);
    }

    @Test
    void ca89_uneEcritureRefuseeParUnePanneDeGoogleLeveUnePannePassagereEtJamaisLeCorpsDeLaReponse() {
        reponse = Response.status(503);

        assertThatThrownBy(() -> client().fill(FICHIER, 4242, List.of(ligne("F", null, "")))).isInstanceOf(TransientGoogleException.class).hasMessage("HTTP_503");
        assertThatThrownBy(() -> client().replace(FICHIER, 4242, 77, "Reçus générés", false, 2)).isInstanceOf(TransientGoogleException.class).hasMessage("HTTP_503");
        assertThatThrownBy(() -> client().deleteTab(FICHIER, 4242)).isInstanceOf(TransientGoogleException.class).hasMessage("HTTP_503");
    }

    @Test
    void a03_unFichierHostileEstEncodeDansLAdresseDeChaqueOperation() {
        String fichier = "../x?y=1#z";

        client().addTab(fichier, 1, "t");
        client().fill(fichier, 1, List.of());
        client().replace(fichier, 1, 2, "t", false, 1);
        client().deleteTab(fichier, 1);

        assertThat(sheets.requests()).hasSize(4).allSatisfy(requete -> assertThat(requete.rawPath()).isEqualTo("/v4/spreadsheets/..%2Fx%3Fy%3D1%23z:batchUpdate"));
    }
}
