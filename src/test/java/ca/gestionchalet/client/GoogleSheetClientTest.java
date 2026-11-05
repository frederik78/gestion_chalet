package ca.gestionchalet.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.client.SheetClient.SheetRow;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.SheetTarget;
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
import java.util.Map;
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
    private static final List<String> ENTETES = List.of("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO");
    private List<String> entetes = ENTETES;
    /** Sous les en-têtes (rang 0) : « juin » (rang 1) et ses deux lignes, une ligne vide, puis « octobre » (rang 5) et sa ligne. */
    private static final List<List<String>> SECTIONS = List.of(List.of("juin"), List.of("6 juin 2026", "Hydro Québec"), List.of("15 juin 2026", "Ménage"), List.of(),
            List.of("octobre"), List.of("1 octobre 2026", "Familiprix"));
    private List<List<String>> lignes = SECTIONS;
    private int lignesDeLOnglet = 100;
    private int colonnesDeLOnglet = 26;
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

    /** Un fichier à l'onglet « Comptes 2026 » (identifiant 7) de {@code lignesDeLOnglet} lignes, dont celles de {@code entetes} et de {@code lignes} sont garnies. */
    private Response repondre(Request requete) {
        if (requete.is("GET", "/v4/spreadsheets/" + FICHIER)) {
            return Response.json("{\"sheets\":[{\"properties\":{\"sheetId\":3,\"title\":\"Autre\",\"gridProperties\":{\"rowCount\":5,\"columnCount\":26}}},"
                    + "{\"properties\":{\"sheetId\":7,\"title\":\"" + ONGLET + "\",\"gridProperties\":{\"rowCount\":" + lignesDeLOnglet + ",\"columnCount\":" + colonnesDeLOnglet + "}}}]}");
        }
        if (requete.is("POST", "/v4/spreadsheets/" + FICHIER + "/developerMetadata:search")) {
            return Response.json(reponseRecherche);
        }
        if (requete.is("GET", "/v4/spreadsheets/" + FICHIER + "/values/'" + ONGLET + "'")) {
            List<List<String>> grille = new ArrayList<>();
            grille.add(entetes);
            grille.addAll(lignes);
            return Response.json(JSON.writeValueAsString(Map.of("values", grille)));
        }
        if (requete.is("POST", "/v4/spreadsheets/" + FICHIER + ":batchUpdate")) {
            return Response.json("{}");
        }
        return Response.status(404);
    }

    private void ecrire(SheetRow ligne) {
        ecrire(new SheetTarget(FICHIER, ONGLET), ligne);
    }

    private void ecrire(SheetTarget cible, SheetRow ligne) {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrique.setReadTimeout(Duration.ofSeconds(3));
        new GoogleSheetClient(RestClient.builder().requestFactory(fabrique).baseUrl(sheets.address()).build(), () -> jetons.get(), LIEN).write(cible, ligne);
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

    /** Les cellules écrites dans la ligne, une demande par colonne, rangées par colonne : DATE, BOUTIQUE, OBJET, TYPE, MONTANT HT, TPS, TVQ, MONTANT TOTAL, NOTES, PHOTO dans l'ordre observé. */
    private JsonNode cellules() {
        Map<Integer, JsonNode> parColonne = new java.util.TreeMap<>();
        for (JsonNode demande : corps(lot()).get("requests")) {
            if (demande.has("updateCells") && demande.at("/updateCells/start/rowIndex").asInt() != 0) {
                parColonne.put(demande.at("/updateCells/start/columnIndex").asInt(), demande.at("/updateCells/rows/0/values/0"));
            }
        }
        return JSON.valueToTree(new ArrayList<>(parColonne.values()));
    }

    /** La cellule écrite sous cette colonne du Sheet, ou le vide : la ligne écrite n'est jamais celle des en-têtes (rang 0). */
    private JsonNode cellule(int colonne) {
        for (JsonNode demande : corps(lot()).get("requests")) {
            if (demande.has("updateCells") && demande.at("/updateCells/start/rowIndex").asInt() != 0 && demande.at("/updateCells/start/columnIndex").asInt() == colonne) {
                return demande.at("/updateCells/rows/0/values/0");
            }
        }
        return JSON.missingNode();
    }

    @Test
    void ca38_laLigneEcriteVautLesValeursDuRecuEnVraiesDatesEtEnNombres() {
        ecrire(garageIberville());

        JsonNode cellules = cellules();
        assertThat(cellules).hasSize(10);
        assertThat(cellules.get(0).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("46189");
        JsonNode format = demande("repeatCell");
        assertThat(format.at("/cell/userEnteredFormat/numberFormat/type").asString()).isEqualTo("DATE");
        assertThat(format.at("/cell/userEnteredFormat/numberFormat/pattern").asString()).isEqualTo("d mmmm yyyy");
        assertThat(format.get("fields").asString()).isEqualTo("userEnteredFormat.numberFormat");
        assertThat(format.at("/range/sheetId").asInt()).isEqualTo(7);
        assertThat(format.at("/range/startRowIndex").asInt()).isEqualTo(4);
        assertThat(format.at("/range/endRowIndex").asInt()).isEqualTo(5);
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
    void ca30_chaqueValeurArriveSousSonEnTeteMemeSiLesColonnesSontDansUnAutreOrdre() {
        entetes = List.of("NOTES", "PHOTO", "TVQ", "TPS", "MONTANT TOTAL", "MONTANT HT", "TYPE", "OBJET", "BOUTIQUE", "DATE");

        ecrire(avec("Garage Iberville", "Pneus", new BigDecimal("30.91"), "Pièce à vérifier : description", List.of(new PhotoLink("Photo 1", "fichier-a"))));

        assertThat(cellule(9).at("/userEnteredValue/numberValue").decimalValue()).as("DATE").isEqualByComparingTo("46306");
        assertThat(cellule(8).at("/userEnteredValue/stringValue").asString()).as("BOUTIQUE").isEqualTo("Garage Iberville");
        assertThat(cellule(7).at("/userEnteredValue/stringValue").asString()).as("OBJET").isEqualTo("Pneus");
        assertThat(cellule(6).at("/userEnteredValue/stringValue").asString()).as("TYPE").isEqualTo("Fournitures");
        assertThat(cellule(5).has("userEnteredValue")).as("MONTANT HT absent").isFalse();
        assertThat(cellule(3).at("/userEnteredValue/numberValue").decimalValue()).as("TPS").isEqualByComparingTo("30.91");
        assertThat(cellule(2).has("userEnteredValue")).as("TVQ absente").isFalse();
        assertThat(cellule(4).at("/userEnteredValue/numberValue").decimalValue()).as("MONTANT TOTAL").isEqualByComparingTo("35.00");
        assertThat(cellule(0).at("/userEnteredValue/stringValue").asString()).as("NOTES").isEqualTo("Pièce à vérifier : description");
        assertThat(cellule(1).at("/userEnteredValue/stringValue").asString()).as("PHOTO").isEqualTo("Photo 1");
        assertThat(demande("repeatCell").at("/range/startColumnIndex").asInt()).as("le format de date suit la colonne DATE").isEqualTo(9);
        assertThat(demande("repeatCell").at("/range/endColumnIndex").asInt()).isEqualTo(10);
    }

    @Test
    void ca30_lesEnTetesSeLisentSansTenirCompteDeLaCasseNiDesAccentsEtUneColonneInconnueNEstJamaisEcrite() {
        entetes = List.of("date", "Boutique", "CALCUL", "Objet", "Type", "montant ht", "Tps", "TVQ", "Montant Total", "Notes", "photo");

        ecrire(garageIberville());

        assertThat(cellule(1).at("/userEnteredValue/stringValue").asString()).isEqualTo("Garage Iberville");
        assertThat(cellule(2).isMissingNode()).as("la colonne CALCUL du propriétaire n'est jamais écrite, donc jamais effacée").isTrue();
        assertThat(cellule(5).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("618.27");
        assertThat(cellule(8).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("710.85");
        assertThat(cellule(0).at("/userEnteredValue/numberValue").isNumber()).isTrue();
    }

    @Test
    void ca43_sansEnTeteBoutiqueAucuneEcritureEtLaCopieEstBloqueePourStructure() {
        entetes = List.of("DATE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO");

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.STRUCTURE_SHEET));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void ca43_r24_unEnTeteEnDoubleBloquePourStructureSansRienEcrire() {
        entetes = List.of("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "tps", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO");

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.STRUCTURE_SHEET));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void ca43_r24_unOngletSansAucuneLigneDEnTetesBloquePourStructureSansRienEcrire() {
        entetes = List.of("n'importe quoi", "=HYPERLINK(\"https://exemple.invalid\")");

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.STRUCTURE_SHEET));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void r24_unEnTeteHostileDuSheetEstIgnoreSansEtreRecopieDansLEnvoi() {
        String formule = "=IMPORTXML(\"https://exemple.invalid\",\"//a\")";
        entetes = List.of("DATE", "BOUTIQUE", formule, "x".repeat(50_000), "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO");

        ecrire(garageIberville());

        assertThat(cellule(1).at("/userEnteredValue/stringValue").asString()).isEqualTo("Garage Iberville");
        assertThat(cellule(4).isMissingNode()).as("OBJET").isFalse();
        assertThat(new String(lot().body(), StandardCharsets.UTF_8)).doesNotContain("IMPORTXML").doesNotContain("xxxxxxxxxx");
    }

    @Test
    void r24_unEnTeteAcceptePasseLaBorneDeCentCaracteresMaisPasCentUn() {
        entetes = List.of("DATE" + " ".repeat(96), "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO");
        ecrire(garageIberville());
        assertThat(cellule(1).at("/userEnteredValue/stringValue").asString()).isEqualTo("Garage Iberville");
        sheets.forget();

        entetes = List.of("DATE" + " ".repeat(97), "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "PHOTO");
        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.STRUCTURE_SHEET));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void a03_unOngletEtUnFichierHostilesSontEncodesDansLesAdressesEtLesGuillemetsDoubles() {
        String fichier = "../x?y=1#z";
        String onglet = "L'été/2026?#";
        sheets.answer(requete -> requete.method().equals("GET") && requete.path().equals("/v4/spreadsheets/" + fichier + "/values/'L''été/2026?#'") ? Response.json("{\"values\":[[\"DATE\",\"BOUTIQUE\",\"OBJET\",\"TYPE\",\"MONTANT HT\",\"TPS\",\"TVQ\",\"MONTANT TOTAL\",\"NOTES\",\"PHOTO\"],[\"juin\"],[\"6 juin 2026\"]]}")
                : requete.method().equals("GET") ? Response.json("{\"sheets\":[{\"properties\":{\"sheetId\":7,\"title\":\"" + onglet + "\",\"gridProperties\":{\"rowCount\":100}}}]}")
                : Response.json("{}"));

        ecrire(new SheetTarget(fichier, onglet), garageIberville());

        assertThat(sheets.requests()).allSatisfy(requete -> assertThat(requete.rawPath()).startsWith("/v4/spreadsheets/..%2Fx%3Fy%3D1%23z"));
        assertThat(sheets.requests().stream().map(Request::rawPath)).contains("/v4/spreadsheets/..%2Fx%3Fy%3D1%23z/values/%27L%27%27%C3%A9t%C3%A9%2F2026%3F%23%27");
        assertThat(sheets.requests()).anySatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void ca42_unOngletSansColonnePhotoLaRecoitApresLaDerniereColonneEtLeLienEstEcritSousElle() {
        entetes = List.of("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES");

        ecrire(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "fichier-a"))));

        JsonNode enTete = corps(lot()).get("requests").findValues("updateCells").stream().filter(demande -> demande.at("/start/rowIndex").asInt() == 0).findFirst().orElseThrow();
        assertThat(enTete.at("/start/sheetId").asInt()).isEqualTo(7);
        assertThat(enTete.at("/start/columnIndex").asInt()).isEqualTo(9);
        assertThat(enTete.at("/rows/0/values/0/userEnteredValue/stringValue").asString()).isEqualTo("PHOTO");
        assertThat(enTete.get("fields").asString()).isEqualTo("userEnteredValue");
        assertThat(cellule(9).at("/userEnteredValue/stringValue").asString()).isEqualTo("Photo 1");
        assertThat(cellule(9).at("/textFormatRuns/0/format/link/uri").asString()).isEqualTo("https://drive.exemple.invalid/fichier/fichier-a/voir");
        assertThat(corps(lot()).get("requests").findValues("appendDimension").stream().filter(demande -> demande.has("dimension") && demande.get("dimension").asString().equals("COLUMNS")))
                .as("l'onglet a de la place pour une colonne de plus").isEmpty();
    }

    @Test
    void ca42_lApresLaDerniereColonneEstApresLaDerniereEnTeteNonVideMemeUneColonneDuProprietaire() {
        entetes = List.of("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES", "CALCUL", " ");

        ecrire(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "fichier-a"))));

        assertThat(cellule(10).at("/userEnteredValue/stringValue").asString()).isEqualTo("Photo 1");
        assertThat(corps(lot()).get("requests").findValues("updateCells").stream().filter(demande -> demande.at("/start/rowIndex").asInt() == 0).findFirst().orElseThrow()
                .at("/start/columnIndex").asInt()).isEqualTo(10);
        assertThat(cellule(9).isMissingNode()).as("CALCUL").isTrue();
    }

    @Test
    void ca42_sansPlaceDansLOngletUneColonneEstAjouteeAvantLEcritureDeLEnTete() {
        entetes = List.of("DATE", "BOUTIQUE", "OBJET", "TYPE", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL", "NOTES");
        colonnesDeLOnglet = 9;

        ecrire(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "fichier-a"))));

        JsonNode demandes = corps(lot()).get("requests");
        assertThat(demandes.get(0).at("/appendDimension/sheetId").asInt()).isEqualTo(7);
        assertThat(demandes.get(0).at("/appendDimension/dimension").asString()).isEqualTo("COLUMNS");
        assertThat(demandes.get(0).at("/appendDimension/length").asInt()).isEqualTo(1);
        assertThat(demandes.get(1).at("/updateCells/rows/0/values/0/userEnteredValue/stringValue").asString()).isEqualTo("PHOTO");
    }

    @Test
    void ca42_unOngletQuiALaColonnePhotoNeVoitSonEnTeteNiSonEspaceModifies() {
        ecrire(avec("F", null, null, "", List.of()));

        assertThat(corps(lot()).get("requests").findValues("updateCells").stream().filter(demande -> demande.at("/start/rowIndex").asInt() == 0)).isEmpty();
        assertThat(corps(lot()).get("requests").findValues("appendDimension").stream().filter(demande -> demande.get("dimension").asString().equals("COLUMNS"))).isEmpty();
    }

    private static SheetRow datee(LocalDate date) {
        return new SheetRow(LIGNE, date, "Fournisseur", null, "Fournitures", null, null, null, new BigDecimal("35.00"), "", List.of());
    }

    private JsonNode demandes() {
        return corps(lot()).get("requests");
    }

    /** Les demandes d'un type, dans l'ordre où elles partent. */
    private List<JsonNode> demandesDe(String type) {
        return demandes().findValues(type);
    }

    private static List<List<String>> titres(String... moisEtLignes) {
        List<List<String>> grille = new ArrayList<>();
        for (String texte : moisEtLignes) {
            grille.add(texte.isEmpty() ? List.of() : List.of(texte.split("\\|")));
        }
        return grille;
    }

    @Test
    void ca41_uneLigneInsereeApresUneLigneDeDonneesReprendSaMiseEnFormeEtLaListeDeroulanteDeType() {
        ecrire(garageIberville());

        List<JsonNode> demandes = new ArrayList<>();
        demandes().forEach(demandes::add);
        assertThat(demandes.get(0).at("/insertDimension/range/sheetId").asInt()).isEqualTo(7);
        assertThat(demandes.get(0).at("/insertDimension/range/dimension").asString()).isEqualTo("ROWS");
        assertThat(demandes.get(0).at("/insertDimension/range/startIndex").asInt()).isEqualTo(4);
        assertThat(demandes.get(0).at("/insertDimension/range/endIndex").asInt()).isEqualTo(5);
        JsonNode format = demandes.get(1).get("copyPaste");
        assertThat(format.get("pasteType").asString()).isEqualTo("PASTE_FORMAT");
        assertThat(format.at("/source")).isEqualTo(JSON.readTree("{\"sheetId\":7,\"startRowIndex\":3,\"endRowIndex\":4,\"startColumnIndex\":0,\"endColumnIndex\":10}"));
        assertThat(format.at("/destination")).isEqualTo(JSON.readTree("{\"sheetId\":7,\"startRowIndex\":4,\"endRowIndex\":5,\"startColumnIndex\":0,\"endColumnIndex\":10}"));
        JsonNode liste = demandes.get(2).get("copyPaste");
        assertThat(liste.get("pasteType").asString()).as("PASTE_FORMAT exclut la validation des données (documentation de Google)").isEqualTo("PASTE_DATA_VALIDATION");
        assertThat(liste.at("/source")).isEqualTo(JSON.readTree("{\"sheetId\":7,\"startRowIndex\":3,\"endRowIndex\":4,\"startColumnIndex\":3,\"endColumnIndex\":4}"));
        assertThat(liste.at("/destination")).isEqualTo(JSON.readTree("{\"sheetId\":7,\"startRowIndex\":4,\"endRowIndex\":5,\"startColumnIndex\":3,\"endColumnIndex\":4}"));
        int dernierCopyPaste = -1;
        int formatDeDate = -1;
        for (int i = 0; i < demandes.size(); i++) {
            dernierCopyPaste = demandes.get(i).has("copyPaste") ? i : dernierCopyPaste;
            formatDeDate = demandes.get(i).has("repeatCell") ? i : formatDeDate;
        }
        assertThat(formatDeDate).as("le format de la date posé en dernier l'emporte sur celui de la ligne copiée").isGreaterThan(dernierCopyPaste);
    }

    @Test
    void ca41_leTypeEtLaMiseEnFormeSuiventLesColonnesDeLEnTeteEtLaLigneCopieeEstLaDerniereDeLaSection() {
        entetes = List.of("NOTES", "TYPE", "PHOTO", "DATE", "BOUTIQUE", "OBJET", "MONTANT HT", "TPS", "TVQ", "MONTANT TOTAL");

        ecrire(garageIberville());

        assertThat(demandesDe("copyPaste").get(0).at("/source/endColumnIndex").asInt()).isEqualTo(10);
        assertThat(demandesDe("copyPaste").get(1).at("/source/startColumnIndex").asInt()).isEqualTo(1);
        assertThat(demandesDe("copyPaste").get(1).at("/destination/startColumnIndex").asInt()).isEqualTo(1);
    }

    @Test
    void ca41_unePositionSansLigneDeDonneesAuDessusPrendLaPlusProcheEnDessousEtSonRangEstDecaleParLInsertion() {
        lignes = titres("juin", "", "octobre", "1 octobre 2026|Familiprix");

        ecrire(garageIberville());

        assertThat(demandesDe("insertDimension").get(0).at("/range/startIndex").asInt()).as("juste après le titre de juin").isEqualTo(2);
        assertThat(demandesDe("copyPaste").get(0).at("/source/startRowIndex").asInt()).as("la ligne d'octobre, décalée d'un rang par l'insertion").isEqualTo(5);
        assertThat(demandesDe("copyPaste").get(0).at("/destination/startRowIndex").asInt()).isEqualTo(2);
    }

    @Test
    void ca41_unOngletSansAucuneLigneDeDonneesNeCopieRienEtNEnInventePas() {
        lignes = titres("juin");

        ecrire(garageIberville());

        assertThat(demandesDe("copyPaste")).isEmpty();
        assertThat(demandesDe("insertDimension").get(0).at("/range/startIndex").asInt()).isEqualTo(2);
    }

    @Test
    void r22_uneLigneSePlaceApresLaDerniereLigneDeSaSectionEtAvantLaLigneVideQuiLaSepareDeLaSuivante() {
        lignes = titres("juin", "6 juin 2026|A", "7 juin 2026|B", "", "", "octobre", "1 octobre 2026|Familiprix");

        ecrire(garageIberville());

        assertThat(demandesDe("insertDimension").get(0).at("/range/startIndex").asInt()).isEqualTo(4);
        assertThat(demandesDe("copyPaste").get(0).at("/source/startRowIndex").asInt()).isEqualTo(3);
    }

    @Test
    void r22_laSectionDuMoisSeReconnaitSansCasseNiAccentsEtLaDerniereSectionVaJusquAuBoutDeLOnglet() {
        lignes = titres("JUIN", "6 juin 2026|A", "", "AOÛT", "3 août 2026|B", "4 août 2026|C");

        ecrire(datee(LocalDate.of(2026, 8, 20)));

        assertThat(demandesDe("insertDimension").get(0).at("/range/startIndex").asInt()).isEqualTo(7);
        assertThat(demandesDe("copyPaste").get(0).at("/source/startRowIndex").asInt()).isEqualTo(6);
    }

    @Test
    void r22_ceNEstUnTitreQueLaSeuleCelluleNonVideDeLaPremiereColonne() {
        lignes = titres("juin", "6 juin 2026|A", "mars|autre chose", "|mars", "Mars de pluie");

        ecrire(datee(LocalDate.of(2026, 6, 20)));

        assertThat(demandesDe("insertDimension").get(0).at("/range/startIndex").asInt()).as("aucune de ces lignes n'est la section « mars » : « juin » court jusqu'au bout").isEqualTo(6);
    }

    @Test
    void ca43_deuxSectionsDuMemeMoisBloquentPourStructureSansRienEcrire() {
        lignes = titres("mars", "3 mars 2026|A", "", "juin", "6 juin 2026|B", "", "Mars", "9 mars 2026|C");

        assertThatThrownBy(() -> ecrire(datee(LocalDate.of(2026, 3, 20)))).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.STRUCTURE_SHEET));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void ca43_deuxSectionsDUnAutreMoisQueCeluiDuRecuBloquentAussiLaCopie() {
        lignes = titres("mars", "3 mars 2026|A", "", "mars", "9 mars 2026|C", "", "juin", "6 juin 2026|B");

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.STRUCTURE_SHEET));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void ca43_r23_unMoisSansSectionBloquePourStructureEnAttendantLaCreationDeSectionsDe001_07() {
        lignes = titres("juin", "6 juin 2026|A");

        assertThatThrownBy(() -> ecrire(datee(LocalDate.of(2026, 3, 20)))).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.STRUCTURE_SHEET));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void r24_unTitreHostileDuSheetEstIgnoreSansEtreRecopieDansLEnvoi() {
        lignes = titres("juin", "6 juin 2026|A", "=IMPORTXML(\"https://exemple.invalid\",\"//a\")", "x".repeat(50_000), "octobre");

        ecrire(garageIberville());

        assertThat(demandesDe("insertDimension").get(0).at("/range/startIndex").asInt()).as("les deux lignes hostiles sont de la section de juin").isEqualTo(5);
        assertThat(new String(lot().body(), StandardCharsets.UTF_8)).doesNotContain("IMPORTXML").doesNotContain("xxxxxxxxxx");
    }

    @Test
    void r21_uneLigneDejaEcriteNeCherchePasDeSectionNiNInsereRienEtGardeSaMiseEnForme() {
        lignes = titres("octobre", "1 octobre 2026|A");
        reponseRecherche = "{\"matchedDeveloperMetadata\":[{\"developerMetadata\":{\"location\":{\"dimensionRange\":"
                + "{\"sheetId\":7,\"dimension\":\"ROWS\",\"startIndex\":2,\"endIndex\":3}}}}]}";

        ecrire(garageIberville());

        assertThat(demandesDe("insertDimension")).isEmpty();
        assertThat(demandesDe("copyPaste")).isEmpty();
        assertThat(demandesDe("appendDimension")).isEmpty();
    }

    @Test
    void ca38_lesMontantsSontDesNombresDuCorpsJsonEtNonDesTextes() {
        ecrire(garageIberville());

        assertThat(cellules().get(4).at("/userEnteredValue/numberValue").isNumber()).isTrue();
        assertThat(new String(lot().body(), StandardCharsets.UTF_8)).contains("\"numberValue\":618.27").contains("\"numberValue\":710.85");
    }

    @Test
    void ca13_uneTaxeAbsenteDonneUneCelluleVideEtUnZeroUneCelluleAZero() {
        ecrire(avec("F", null, null, "", List.of()));
        JsonNode sansTps = cellules();
        sheets.forget();
        ecrire(avec("F", null, BigDecimal.ZERO, "", List.of()));
        JsonNode avecZero = cellules();

        assertThat(sansTps.get(5).has("userEnteredValue")).isFalse();
        assertThat(avecZero.get(5).at("/userEnteredValue/numberValue").isNumber()).isTrue();
        assertThat(avecZero.get(5).at("/userEnteredValue/numberValue").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void ca40_sansAlerteLaCelluleNotesEstVide() {
        ecrire(avec("F", null, null, "", List.of()));

        assertThat(cellules().get(8).has("userEnteredValue")).isFalse();
        assertThat(cellules().get(2).has("userEnteredValue")).isFalse();
    }

    @Test
    void ca44_lesLiensDeLaCellulePhotoVisentLesFichiersDriveDansUneSeuleCellule() {
        ecrire(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "fichier-a"), new PhotoLink("Photo 2", "fichier-b"))));

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
        ecrire(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "a/b?c=d#e"))));

        assertThat(cellules().get(9).at("/textFormatRuns/0/format/link/uri").asString()).isEqualTo("https://drive.exemple.invalid/fichier/a%2Fb%3Fc=d%23e/voir");
    }

    @Test
    void ca44_unSeulLienNeLaisseAucunPassageSansLien() {
        ecrire(avec("F", null, null, "", List.of(new PhotoLink("Photo 1", "fichier-a"))));

        assertThat(cellules().get(9).get("textFormatRuns")).hasSize(1);
        assertThat(cellules().get(9).at("/userEnteredValue/stringValue").asString()).isEqualTo("Photo 1");
    }

    @Test
    void cs16_desValeursQuiRessemblentADesFormulesSontEcritesCommeTextesLitteraux() {
        ecrire(avec("=IMPORTXML(\"https://exemple.invalid\",\"//a\")", "+SOMME(1;1)", null, "@notes", List.of()));

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
        ecrire(garageIberville());

        assertThat(corps(lot()).get("requests")).hasSize(15);
        assertThat(demande("updateCells").at("/start/sheetId").asInt()).isEqualTo(7);
        assertThat(demande("updateCells").at("/start/rowIndex").asInt()).isEqualTo(4);
        assertThat(demande("updateCells").at("/start/columnIndex").asInt()).isZero();
        JsonNode metadonnee = demande("createDeveloperMetadata").get("developerMetadata");
        assertThat(metadonnee.get("metadataKey").asString()).isEqualTo("recuId");
        assertThat(metadonnee.get("metadataValue").asString()).isEqualTo(LIGNE);
        assertThat(metadonnee.get("visibility").asString()).isEqualTo("DOCUMENT");
        assertThat(metadonnee.at("/location/dimensionRange/sheetId").asInt()).isEqualTo(7);
        assertThat(metadonnee.at("/location/dimensionRange/dimension").asString()).isEqualTo("ROWS");
        assertThat(metadonnee.at("/location/dimensionRange/startIndex").asInt()).isEqualTo(4);
        assertThat(metadonnee.at("/location/dimensionRange/endIndex").asInt()).isEqualTo(5);
    }

    @Test
    void r21_sansPlaceDansLOngletUneLigneEstAjouteeDansLeMemeLot() {
        lignesDeLOnglet = 4;

        ecrire(garageIberville());

        JsonNode demandes = corps(lot()).get("requests");
        assertThat(demandes).hasSize(15);
        assertThat(demandes.get(0).at("/appendDimension/sheetId").asInt()).isEqualTo(7);
        assertThat(demandes.get(0).at("/appendDimension/dimension").asString()).isEqualTo("ROWS");
        assertThat(demandes.get(0).at("/appendDimension/length").asInt()).isEqualTo(1);
        assertThat(demandes.get(1).has("copyPaste")).isTrue();
        assertThat(demandes.get(14).has("createDeveloperMetadata")).isTrue();
    }

    @Test
    void r21_uneLigneDejaEcriteEstRetrouveeParSonIdentifiantEtMiseAJourSansCreation() {
        reponseRecherche = "{\"matchedDeveloperMetadata\":[{\"developerMetadata\":{\"location\":{\"dimensionRange\":"
                + "{\"sheetId\":7,\"dimension\":\"ROWS\",\"startIndex\":4,\"endIndex\":5}}}}]}";

        ecrire(garageIberville());

        Request recherche = sheets.requests().stream().filter(requete -> requete.path().endsWith("developerMetadata:search")).findFirst().orElseThrow();
        assertThat(corps(recherche).at("/dataFilters/0/developerMetadataLookup/metadataKey").asString()).isEqualTo("recuId");
        assertThat(corps(recherche).at("/dataFilters/0/developerMetadataLookup/metadataValue").asString()).isEqualTo(LIGNE);
        assertThat(corps(lot()).get("requests")).hasSize(11);
        assertThat(demande("updateCells").at("/start/rowIndex").asInt()).isEqualTo(4);
        assertThat(demande("repeatCell").at("/range/startRowIndex").asInt()).isEqualTo(4);
        assertThat(demande("createDeveloperMetadata").isMissingNode()).isTrue();
    }

    @Test
    void r21_uneLigneRetrouveeDansUnAutreOngletNEstPasCelleDeLOnglet() {
        reponseRecherche = "{\"matchedDeveloperMetadata\":[{\"developerMetadata\":{\"location\":{\"dimensionRange\":"
                + "{\"sheetId\":3,\"dimension\":\"ROWS\",\"startIndex\":4,\"endIndex\":5}}}}]}";

        ecrire(garageIberville());

        assertThat(demande("updateCells").at("/start/sheetId").asInt()).isEqualTo(7);
        assertThat(demande("updateCells").at("/start/rowIndex").asInt()).isEqualTo(4);
    }

    @Test
    void r21_aucuneRequeteNeVaVersUnAutreOngletQueCeluiDeLaConfiguration() {
        ecrire(garageIberville());

        assertThat(sheets.requests()).allSatisfy(requete -> assertThat(requete.path()).startsWith("/v4/spreadsheets/" + FICHIER));
        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).contains("Autre"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void r24_sansFichierOuSansOngletConfiguresAucuneRequeteNePartEtLaCopieEstBloquee(String vide) {
        assertThatThrownBy(() -> ecrire(new SheetTarget(vide, ONGLET), garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));
        assertThatThrownBy(() -> ecrire(new SheetTarget(FICHIER, vide), garageIberville())).isInstanceOf(BlockedGoogleException.class);

        assertThat(sheets.requests()).isEmpty();
    }

    @Test
    void r24_unOngletAbsentBloqueLaCopieSansRienEcrire() {
        assertThatThrownBy(() -> ecrire(new SheetTarget(FICHIER, "Comptes 2030"), garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));

        assertThat(sheets.requests()).noneSatisfy(requete -> assertThat(requete.path()).endsWith(":batchUpdate"));
    }

    @Test
    void r20_sansAutorisationAucuneRequeteNePart() {
        jetons = Optional::empty;

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.AUTORISATION_GOOGLE));

        assertThat(sheets.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503, 401})
    void r20_uneReponse429OuServeurOuUnJetonRefuseEstUnePannePassagere(int statut) {
        sheets.answer(requete -> Response.status(statut));

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOf(TransientGoogleException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    void r20_unFichierRefuseOuIntrouvableBloqueLaCopie(int statut) {
        sheets.answer(requete -> Response.status(statut));

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));
    }

    @Test
    void r20_uneReponseDeSuccesIllisibleEstUnePannePassagere() {
        sheets.answer(requete -> Response.text(200, "<html>mandataire</html>"));

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOf(TransientGoogleException.class);
    }

    @Test
    void r20_uneEcritureQuiEchoueApresLaLectureEstUnePannePassagereSansRienPerdre() {
        sheets.answer(requete -> requete.path().endsWith(":batchUpdate") ? Response.status(503) : repondre(requete));

        assertThatThrownBy(() -> ecrire(garageIberville())).isInstanceOf(TransientGoogleException.class);
    }
}
