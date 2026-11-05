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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** L'adaptateur du Sheet contre un serveur local : le format exact des requêtes (littéral d'écriture, liens, identifiant invisible de ligne) et le classement des pannes. */
class GoogleSheetClientTest {

    private static final String FICHIER = "fichier-essai";
    private static final String ONGLET = "Comptes 2026";
    private static final String LIEN = "https://drive.exemple.invalid/fichier/{id}/voir";
    private static final String LIGNE = "22222222-2222-4222-8222-222222222222";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ApiStub sheets;
    private Supplier<Optional<String>> jetons = () -> Optional.of("jeton-d-essai");
    private int lignesUtilisees = 12;
    private int lignesDeLOnglet = 100;
    private String reponseRecherche = "{}";

    @BeforeEach
    void demarrer() throws IOException {
        sheets = new ApiStub();
        sheets.answer(this::repondre);
    }

    @AfterEach
    void arreter() {
        sheets.close();
    }

    /** Un fichier à l'onglet « Comptes 2026 » (identifiant 7) de {@code lignesDeLOnglet} lignes, dont {@code lignesUtilisees} garnies. */
    private Response repondre(Request requete) {
        if (requete.is("GET", "/v4/spreadsheets/" + FICHIER)) {
            return Response.json("{\"sheets\":[{\"properties\":{\"sheetId\":3,\"title\":\"Autre\",\"gridProperties\":{\"rowCount\":5}}},"
                    + "{\"properties\":{\"sheetId\":7,\"title\":\"" + ONGLET + "\",\"gridProperties\":{\"rowCount\":" + lignesDeLOnglet + "}}}]}");
        }
        if (requete.is("POST", "/v4/spreadsheets/" + FICHIER + "/developerMetadata:search")) {
            return Response.json(reponseRecherche);
        }
        if (requete.is("GET", "/v4/spreadsheets/" + FICHIER + "/values/'" + ONGLET + "'!A:Z")) {
            return Response.json("{\"values\":[" + "[\"x\"],".repeat(Math.max(0, lignesUtilisees - 1)) + (lignesUtilisees > 0 ? "[\"x\"]" : "") + "]}");
        }
        if (requete.is("POST", "/v4/spreadsheets/" + FICHIER + ":batchUpdate")) {
            return Response.json("{}");
        }
        return Response.status(404);
    }

    private GoogleSheetClient client() {
        return client(FICHIER, ONGLET);
    }

    private GoogleSheetClient client(String fichier, String onglet) {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrique.setReadTimeout(Duration.ofSeconds(3));
        return new GoogleSheetClient(RestClient.builder().requestFactory(fabrique).baseUrl(sheets.address()).build(), () -> jetons.get(), fichier, onglet, LIEN);
    }

    private static SheetRow garageIberville() {
        return new SheetRow(LIGNE, LocalDate.of(2026, 6, 16), "Garage Iberville", null, "Entretien / Réparations",
                new BigDecimal("618.27"), new BigDecimal("30.91"), new BigDecimal("61.67"), new BigDecimal("710.85"),
                "Numéros d'inscription manquants : TPS, TVQ ; Pièce à vérifier : nom de l'acheteur, description, conditions de paiement", List.of());
    }

    private static SheetRow avec(String fournisseur, String objet, BigDecimal tps, String notes, List<PhotoLink> photos) {
        return new SheetRow(LIGNE, LocalDate.of(2026, 10, 11), fournisseur, objet, "Fournitures", null, tps, null, new BigDecimal("35.00"), notes, photos);
    }

    private Request lot() {
        return sheets.requests().stream().filter(requete -> requete.path().endsWith(":batchUpdate")).findFirst().orElseThrow();
    }

    private static JsonNode corps(Request requete) {
        return JSON.readTree(new String(requete.body(), StandardCharsets.UTF_8));
    }

    /** La demande du lot qui porte cette clé ({@code updateCells}, {@code repeatCell}…), ou le vide. */
    private JsonNode demande(String type) {
        for (JsonNode demande : corps(lot()).get("requests")) {
            if (demande.has(type)) {
                return demande.get(type);
            }
        }
        return JSON.missingNode();
    }

    /** Les dix cellules de la ligne écrite : DATE, BOUTIQUE, OBJET, TYPE, MONTANT HT, TPS, TVQ, MONTANT TOTAL, NOTES, PHOTO. */
    private JsonNode cellules() {
        return demande("updateCells").get("rows").get(0).get("values");
    }

    @Test
    void ca38_laLigneEcriteVautLesValeursDuRecuEnVraiesDatesEtEnNombres() {
        client().write(garageIberville());

        JsonNode cellules = cellules();
        assertThat(cellules).hasSize(10);
        assertThat(cellules.get(0).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("46189");
        JsonNode format = demande("repeatCell");
        assertThat(format.at("/cell/userEnteredFormat/numberFormat/type").asString()).isEqualTo("DATE");
        assertThat(format.at("/cell/userEnteredFormat/numberFormat/pattern").asString()).isEqualTo("d mmmm yyyy");
        assertThat(format.get("fields").asString()).isEqualTo("userEnteredFormat.numberFormat");
        assertThat(format.at("/range/sheetId").asInt()).isEqualTo(7);
        assertThat(format.at("/range/startRowIndex").asInt()).isEqualTo(12);
        assertThat(format.at("/range/endRowIndex").asInt()).isEqualTo(13);
        assertThat(format.at("/range/startColumnIndex").asInt()).isZero();
        assertThat(format.at("/range/endColumnIndex").asInt()).isEqualTo(1);
        assertThat(demande("updateCells").get("fields").asString()).isEqualTo("userEnteredValue,textFormatRuns");
        assertThat(cellules.get(1).at("/userEnteredValue/stringValue").asString()).isEqualTo("Garage Iberville");
        assertThat(cellules.get(3).at("/userEnteredValue/stringValue").asString()).isEqualTo("Entretien / Réparations");
        assertThat(cellules.get(4).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("618.27");
        assertThat(cellules.get(5).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("30.91");
        assertThat(cellules.get(6).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("61.67");
        assertThat(cellules.get(7).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("710.85");
        assertThat(cellules.get(8).at("/userEnteredValue/stringValue").asString())
                .isEqualTo("Numéros d'inscription manquants : TPS, TVQ ; Pièce à vérifier : nom de l'acheteur, description, conditions de paiement");
    }

    @Test
    void ca38_lesMontantsSontDesNombresDuCorpsJsonEtNonDesTextes() {
        client().write(garageIberville());

        assertThat(cellules().get(4).at("/userEnteredValue/numberValue").isNumber()).isTrue();
        assertThat(new String(lot().body(), StandardCharsets.UTF_8)).contains("\"numberValue\":618.27").contains("\"numberValue\":710.85");
    }

    @Test
    void ca13_uneTaxeAbsenteDonneUneCelluleVideEtUnZeroUneCelluleAZero() {
        client().write(avec("F", null, null, "", List.of()));
        JsonNode sansTps = cellules();
        sheets.forget();
        client().write(avec("F", null, BigDecimal.ZERO, "", List.of()));
        JsonNode avecZero = cellules();

        assertThat(sansTps.get(5).has("userEnteredValue")).isFalse();
        assertThat(avecZero.get(5).at("/userEnteredValue/numberValue").isNumber()).isTrue();
        assertThat(avecZero.get(5).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void ca40_sansAlerteLaCelluleNotesEstVide() {
        client().write(avec("F", null, null, "", List.of()));

        assertThat(cellules().get(8).has("userEnteredValue")).isFalse();
        assertThat(cellules().get(2).has("userEnteredValue")).isFalse();
    }

    @Test
    void ca44_lesLiensDeLaCellulePhotoVisentLesFichiersDriveDansUneSeuleCellule() {
        client().write(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "fichier-a"), new PhotoLink("Photo 2", "fichier-b"))));

        JsonNode photo = cellules().get(9);
        assertThat(photo.at("/userEnteredValue/stringValue").asString()).isEqualTo("Photo 1, Photo 2");
        JsonNode passages = photo.get("textFormatRuns");
        assertThat(passages).hasSize(3);
        assertThat(passages.get(0).get("startIndex").asInt()).isZero();
        assertThat(passages.get(0).at("/format/link/uri").asString()).isEqualTo("https://drive.exemple.invalid/fichier/fichier-a/voir");
        assertThat(passages.get(1).get("startIndex").asInt()).isEqualTo(7);
        assertThat(passages.get(1).at("/format/link").isMissingNode()).isTrue();
        assertThat(passages.get(2).get("startIndex").asInt()).isEqualTo(9);
        assertThat(passages.get(2).at("/format/link/uri").asString()).isEqualTo("https://drive.exemple.invalid/fichier/fichier-b/voir");
    }

    @Test
    void a03_unIdentifiantDeFichierNeSortPasDeLAdresseDuLien() {
        client().write(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "a/b?c=d#e"))));

        assertThat(cellules().get(9).at("/textFormatRuns/0/format/link/uri").asString()).isEqualTo("https://drive.exemple.invalid/fichier/a%2Fb%3Fc=d%23e/voir");
    }

    @Test
    void ca44_unSeulLienNeLaisseAucunPassageSansLien() {
        client().write(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "fichier-a"))));

        assertThat(cellules().get(9).get("textFormatRuns")).hasSize(1);
        assertThat(cellules().get(9).at("/userEnteredValue/stringValue").asString()).isEqualTo("Photo 1");
    }

    @Test
    void cs16_desValeursQuiRessemblentADesFormulesSontEcritesCommeTextesLitteraux() {
        client().write(avec("=IMPORTXML(\"https://exemple.invalid\",\"//a\")", "+SOMME(1;1)", null, "@notes", List.of()));

        JsonNode cellules = cellules();
        assertThat(cellules.get(1).at("/userEnteredValue/stringValue").asString()).isEqualTo("=IMPORTXML(\"https://exemple.invalid\",\"//a\")");
        assertThat(cellules.get(2).at("/userEnteredValue/stringValue").asString()).isEqualTo("+SOMME(1;1)");
        assertThat(cellules.get(8).at("/userEnteredValue/stringValue").asString()).isEqualTo("@notes");
        String corps = new String(lot().body(), StandardCharsets.UTF_8);
        assertThat(corps).doesNotContain("formulaValue").doesNotContain("valueInputOption");
        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.query() == null ? "" : requete.query()).contains("valueInputOption"));
    }

    @Test
    void r21_uneNouvelleLigneEstCreeeApresLaDerniereLigneGarnieAvecSonIdentifiantInvisibleDansLeMemeLot() {
        client().write(garageIberville());

        assertThat(corps(lot()).get("requests")).hasSize(3);
        assertThat(demande("updateCells").at("/start/sheetId").asInt()).isEqualTo(7);
        assertThat(demande("updateCells").at("/start/rowIndex").asInt()).isEqualTo(12);
        assertThat(demande("updateCells").at("/start/columnIndex").asInt()).isZero();
        JsonNode metadonnee = demande("createDeveloperMetadata").get("developerMetadata");
        assertThat(metadonnee.get("metadataKey").asString()).isEqualTo("recuId");
        assertThat(metadonnee.get("metadataValue").asString()).isEqualTo(LIGNE);
        assertThat(metadonnee.get("visibility").asString()).isEqualTo("DOCUMENT");
        assertThat(metadonnee.at("/location/dimensionRange/sheetId").asInt()).isEqualTo(7);
        assertThat(metadonnee.at("/location/dimensionRange/dimension").asString()).isEqualTo("ROWS");
        assertThat(metadonnee.at("/location/dimensionRange/startIndex").asInt()).isEqualTo(12);
        assertThat(metadonnee.at("/location/dimensionRange/endIndex").asInt()).isEqualTo(13);
    }

    @Test
    void r21_sansPlaceDansLOngletUneLigneEstAjouteeDansLeMemeLot() {
        lignesDeLOnglet = 12;

        client().write(garageIberville());

        JsonNode demandes = corps(lot()).get("requests");
        assertThat(demandes).hasSize(4);
        assertThat(demandes.get(0).at("/appendDimension/sheetId").asInt()).isEqualTo(7);
        assertThat(demandes.get(0).at("/appendDimension/dimension").asString()).isEqualTo("ROWS");
        assertThat(demandes.get(0).at("/appendDimension/length").asInt()).isEqualTo(1);
        assertThat(demandes.get(1).has("updateCells")).isTrue();
        assertThat(demandes.get(3).has("createDeveloperMetadata")).isTrue();
    }

    @Test
    void r21_uneLigneDejaEcriteEstRetrouveeParSonIdentifiantEtMiseAJourSansCreation() {
        reponseRecherche = "{\"matchedDeveloperMetadata\":[{\"developerMetadata\":{\"location\":{\"dimensionRange\":"
                + "{\"sheetId\":7,\"dimension\":\"ROWS\",\"startIndex\":4,\"endIndex\":5}}}}]}";

        client().write(garageIberville());

        Request recherche = sheets.requests().stream().filter(requete -> requete.path().endsWith("developerMetadata:search")).findFirst().orElseThrow();
        assertThat(corps(recherche).at("/dataFilters/0/developerMetadataLookup/metadataKey").asString()).isEqualTo("recuId");
        assertThat(corps(recherche).at("/dataFilters/0/developerMetadataLookup/metadataValue").asString()).isEqualTo(LIGNE);
        assertThat(corps(lot()).get("requests")).hasSize(2);
        assertThat(demande("updateCells").at("/start/rowIndex").asInt()).isEqualTo(4);
        assertThat(demande("repeatCell").at("/range/startRowIndex").asInt()).isEqualTo(4);
        assertThat(demande("createDeveloperMetadata").isMissingNode()).isTrue();
        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).contains("/values/"));
    }

    @Test
    void r21_uneLigneRetrouveeDansUnAutreOngletNEstPasCelleDeLOnglet() {
        reponseRecherche = "{\"matchedDeveloperMetadata\":[{\"developerMetadata\":{\"location\":{\"dimensionRange\":"
                + "{\"sheetId\":3,\"dimension\":\"ROWS\",\"startIndex\":4,\"endIndex\":5}}}}]}";

        client().write(garageIberville());

        assertThat(demande("updateCells").at("/start/sheetId").asInt()).isEqualTo(7);
        assertThat(demande("updateCells").at("/start/rowIndex").asInt()).isEqualTo(12);
    }

    @Test
    void r21_aucuneRequeteNeVaVersUnAutreOngletQueCeluiDeLaConfiguration() {
        client().write(garageIberville());

        assertThat(sheets.requests()).allSatisfy(requete -> assertThat(requete.path()).startsWith("/v4/spreadsheets/" + FICHIER));
        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).contains("Autre"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void r24_sansFichierOuSansOngletConfiguresAucuneRequeteNePartEtLaCopieEstBloquee(String vide) {
        assertThatThrownBy(() -> client(vide, ONGLET).write(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));
        assertThatThrownBy(() -> client(FICHIER, vide).write(garageIberville())).isInstanceOf(BlockedGoogleException.class);

        assertThat(sheets.requests()).isEmpty();
    }

    @Test
    void r24_unOngletAbsentBloqueLaCopieSansRienEcrire() {
        assertThatThrownBy(() -> client(FICHIER, "Comptes 2030").write(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void r20_sansAutorisationAucuneRequeteNePart() {
        jetons = Optional::empty;

        assertThatThrownBy(() -> client().write(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.AUTORISATION_GOOGLE));

        assertThat(sheets.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503, 401})
    void r20_uneReponse429OuServeurOuUnJetonRefuseEstUnePannePassagere(int statut) {
        sheets.answer(requete -> Response.status(statut));

        assertThatThrownBy(() -> client().write(garageIberville())).isInstanceOf(TransientGoogleException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    void r20_unFichierRefuseOuIntrouvableBloqueLaCopie(int statut) {
        sheets.answer(requete -> Response.status(statut));

        assertThatThrownBy(() -> client().write(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));
    }

    @Test
    void r20_uneReponseDeSuccesIllisibleEstUnePannePassagere() {
        sheets.answer(requete -> Response.text(200, "<html>mandataire</html>"));

        assertThatThrownBy(() -> client().write(garageIberville())).isInstanceOf(TransientGoogleException.class);
    }

    @Test
    void r20_uneEcritureQuiEchoueApresLaLectureEstUnePannePassagereSansRienPerdre() {
        sheets.answer(requete -> requete.path().endsWith(":batchUpdate") ? Response.status(503) : repondre(requete));

        assertThatThrownBy(() -> client().write(garageIberville())).isInstanceOf(TransientGoogleException.class);
    }
}
