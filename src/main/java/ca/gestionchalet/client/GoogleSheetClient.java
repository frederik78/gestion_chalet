package ca.gestionchalet.client;

import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.SheetTarget;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;

/**
 * Google Sheets par son API REST (v4), sans bibliothèque cliente (voir {@link GoogleDriveClient}). Le fichier et l'onglet viennent de l'appelant ({@link SheetTarget}) : aucune requête
 * ne vise un autre onglet. Une ligne s'écrit en un seul lot, donc tout ou rien : ses cellules et son identifiant invisible, une métadonnée de développeur attachée à la ligne, qui la suit
 * quand d'autres lignes s'insèrent (R-21, Q-07 à confirmer à l'essai réel). Les valeurs s'écrivent par {@code userEnteredValue.stringValue}, jamais par {@code formulaValue} ni par un
 * mode de saisie qui interprète le texte (D-19).
 *
 * <p>Les colonnes se repèrent par leur en-tête, PHOTO s'ajoute si elle manque, et la ligne s'insère dans la section de son mois ({@link SheetLayout}) ; une structure inattendue
 * bloque la copie avant toute écriture. Le nom de l'onglet et l'identifiant du fichier, venus de la configuration, sont encodés dans les adresses, et les guillemets simples d'une
 * plage doublés. Ce que le Sheet contient (en-têtes, titres) est une entrée externe : comparé, jamais réécrit ni journalisé.
 *
 * <p>L'onglet généré de la régénération (D-66) se lit et s'écrit par {@link #tabs}, {@link #addTab}, {@link #fill}, {@link #replace} et {@link #deleteTab} : un lot par opération, donc tout ou
 * rien. Le remplacement copie l'onglet temporaire sur l'onglet cible dans un seul lot ; que la copie garde un texte à signe de formule en texte reste à confirmer à l'essai réel (section 13 du PRD).
 *
 * <p><b>Limite, jusqu'au placement par la date (001-07) :</b> la ligne se place à la fin de la section du mois, sans tenir compte de sa date, et un mois sans section bloque la copie.
 */
public class GoogleSheetClient implements SheetClient {

    private static final String METADATA_KEY = "recuId";
    private static final long DAYS_FROM_SHEETS_EPOCH_TO_UNIX_EPOCH = 25569;
    private static final String PHOTO_SEPARATOR = ", ";
    private static final Map<String, Object> GREY_BACKGROUND = Map.of("red", 0.85, "green", 0.85, "blue", 0.85);
    private static final Map<String, Object> GREY_TEXT = Map.of("red", 0.6, "green", 0.6, "blue", 0.6);

    private final RestClient restClient;
    private final GoogleCalls calls;
    private final String photoLinkTemplate;

    /**
     * @param photoLinkTemplate adresse d'un fichier Drive, où {@code {id}} est l'identifiant renvoyé par Drive
     */
    public GoogleSheetClient(RestClient restClient, Supplier<Optional<String>> accessTokens, String photoLinkTemplate) {
        this.restClient = restClient;
        this.calls = new GoogleCalls(accessTokens);
        this.photoLinkTemplate = photoLinkTemplate;
    }

    @Override
    public void write(SheetTarget target, SheetRow row) {
        if (!target.isUsable()) {
            throw new BlockedGoogleException(MotifBlocage.FICHIER_INACCESSIBLE);
        }
        String fileId = target.fileId();
        String tab = target.tab();
        String authorization = calls.bearer();
        JsonNode tabProperties = findTab(authorization, fileId, tab);
        int sheetId = tabProperties.at("/sheetId").asInt();
        Optional<Integer> existing = findRow(authorization, fileId, sheetId, row.rowId());
        SheetLayout layout = SheetLayout.read(readGrid(authorization, fileId, tab));
        Optional<SheetLayout.Placement> placement = existing.isEmpty() ? Optional.of(layout.placementFor(row.date().getMonth())) : Optional.empty();
        int rowIndex = existing.orElseGet(() -> placement.orElseThrow().insertAt());
        List<Map<String, Object>> requests = new ArrayList<>();
        if (layout.photoColumnToAdd()) {
            addPhotoColumn(requests, layout, sheetId, tabProperties.at("/gridProperties/columnCount").asInt());
        }
        placement.ifPresent(where -> requests.addAll(newRow(layout, where, sheetId, tabProperties.at("/gridProperties/rowCount").asInt())));
        for (SheetColumn column : SheetColumn.values()) {
            requests.add(Map.of("updateCells", Map.of(
                    "start", Map.of("sheetId", sheetId, "rowIndex", rowIndex, "columnIndex", layout.columnOf(column)),
                    "rows", List.of(Map.of("values", List.of(cell(column, row)))),
                    "fields", "userEnteredValue,textFormatRuns")));
        }
        int dateColumn = layout.columnOf(SheetColumn.DATE);
        requests.add(Map.of("repeatCell", Map.of(
                "range", Map.of("sheetId", sheetId, "startRowIndex", rowIndex, "endRowIndex", rowIndex + 1, "startColumnIndex", dateColumn, "endColumnIndex", dateColumn + 1),
                "cell", Map.of("userEnteredFormat", Map.of("numberFormat", Map.of("type", "DATE", "pattern", "d mmmm yyyy"))),
                "fields", "userEnteredFormat.numberFormat")));
        if (existing.isEmpty()) {
            requests.add(Map.of("createDeveloperMetadata", Map.of("developerMetadata", Map.of(
                    "metadataKey", METADATA_KEY, "metadataValue", row.rowId(), "visibility", "DOCUMENT",
                    "location", Map.of("dimensionRange", Map.of("sheetId", sheetId, "dimension", "ROWS", "startIndex", rowIndex, "endIndex", rowIndex + 1))))));
        }
        calls.call(() -> restClient.post().uri("/v4/spreadsheets/{id}:batchUpdate", fileId).header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("requests", requests)).retrieve().toBodilessEntity());
    }

    @Override
    public List<SheetTab> tabs(String fileId) {
        String authorization = calls.bearer();
        JsonNode file = calls.call(() -> restClient.get()
                .uri(uri -> uri.path("/v4/spreadsheets/{id}").queryParam("fields", "sheets.properties(sheetId,title)").build(fileId))
                .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(JsonNode.class));
        if (file == null || !file.path("sheets").isArray()) {
            throw new TransientGoogleException("REPONSE_ILLISIBLE");
        }
        List<SheetTab> tabs = new ArrayList<>();
        for (JsonNode sheet : file.get("sheets")) {
            JsonNode properties = sheet.path("properties");
            if (!properties.path("sheetId").isNumber() || !properties.path("title").isString()) {
                throw new TransientGoogleException("REPONSE_ILLISIBLE");
            }
            tabs.add(new SheetTab(properties.get("sheetId").asInt(), properties.get("title").asString()));
        }
        return tabs;
    }

    @Override
    public void addTab(String fileId, int sheetId, String title) {
        batch(fileId, List.of(Map.of("addSheet", Map.of("properties", Map.of("sheetId", sheetId, "title", title)))));
    }

    /**
     * Un seul lot : l'onglet prend sa taille, puis reçoit les en-têtes et les lignes, avec leur mise en forme (le format de la date, celui des montants, les en-têtes en gras et la
     * colonne ID grisée, fond et texte : seule mise en forme de l'onglet généré, section 7.4). Chaque valeur est écrite par {@code stringValue} ou {@code numberValue}, jamais par
     * {@code formulaValue} (D-19, CS16) : l'identifiant du reçu aussi.
     */
    @Override
    public void fill(String fileId, int sheetId, List<SheetRow> rows) {
        List<Map<String, Object>> lines = new ArrayList<>();
        lines.add(Map.of("values", Arrays.stream(GeneratedColumn.values()).map(GoogleSheetClient::headerCell).toList()));
        for (SheetRow row : rows) {
            lines.add(Map.of("values", Arrays.stream(GeneratedColumn.values()).map(column -> generatedCell(column, row)).toList()));
        }
        batch(fileId, List.of(
                Map.of("updateSheetProperties", Map.of(
                        "properties", Map.of("sheetId", sheetId, "gridProperties", gridSize(lines.size())),
                        "fields", "gridProperties(rowCount,columnCount)")),
                Map.of("updateCells", Map.of(
                        "start", Map.of("sheetId", sheetId, "rowIndex", 0, "columnIndex", 0),
                        "rows", lines,
                        "fields", "userEnteredValue,userEnteredFormat,textFormatRuns"))));
    }

    /**
     * Un seul lot, donc tout ou rien (Google applique toutes les demandes d'un lot ou aucune) : la cible est créée si elle manque, reprend son titre et la taille de la source, est
     * vidée de ses cellules (valeurs et mise en forme), puis reçoit celles de la source. Ni suppression ni recréation : l'identité de l'onglet cible est conservée (R-40).
     */
    @Override
    public void replace(String fileId, int sourceSheetId, int targetSheetId, String targetTitle, boolean createTarget, int rowCount) {
        List<Map<String, Object>> requests = new ArrayList<>();
        if (createTarget) {
            requests.add(Map.of("addSheet", Map.of("properties", Map.of("sheetId", targetSheetId, "title", targetTitle))));
        }
        requests.add(Map.of("updateSheetProperties", Map.of(
                "properties", Map.of("sheetId", targetSheetId, "title", targetTitle, "gridProperties", gridSize(rowCount)),
                "fields", "title,gridProperties(rowCount,columnCount)")));
        requests.add(Map.of("updateCells", Map.of("range", Map.of("sheetId", targetSheetId), "fields", "*")));
        requests.add(Map.of("copyPaste", Map.of("source", generatedRange(sourceSheetId, rowCount), "destination", generatedRange(targetSheetId, rowCount),
                "pasteType", "PASTE_NORMAL", "pasteOrientation", "NORMAL")));
        batch(fileId, requests);
    }

    @Override
    public void deleteTab(String fileId, int sheetId) {
        batch(fileId, List.of(Map.of("deleteSheet", Map.of("sheetId", sheetId))));
    }

    private void batch(String fileId, List<Map<String, Object>> requests) {
        String authorization = calls.bearer();
        calls.call(() -> restClient.post().uri("/v4/spreadsheets/{id}:batchUpdate", fileId).header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("requests", requests)).retrieve().toBodilessEntity());
    }

    private static Map<String, Object> gridSize(int rowCount) {
        return Map.of("rowCount", rowCount, "columnCount", GeneratedColumn.values().length);
    }

    private static Map<String, Object> generatedRange(int sheetId, int rowCount) {
        return Map.of("sheetId", sheetId, "startRowIndex", 0, "endRowIndex", rowCount, "startColumnIndex", 0, "endColumnIndex", GeneratedColumn.values().length);
    }

    private static Map<String, Object> headerCell(GeneratedColumn column) {
        Map<String, Object> textFormat = new LinkedHashMap<>();
        textFormat.put("bold", true);
        Map<String, Object> format = new LinkedHashMap<>();
        if (column == GeneratedColumn.ID) {
            format.put("backgroundColor", GREY_BACKGROUND);
            textFormat.put("foregroundColor", GREY_TEXT);
        }
        format.put("textFormat", textFormat);
        return Map.of("userEnteredValue", Map.of("stringValue", column.header()), "userEnteredFormat", format);
    }

    /** Une valeur absente donne une cellule vide (R-08) ; la mise en forme ne dépend que de la colonne. */
    private Map<String, Object> generatedCell(GeneratedColumn column, SheetRow row) {
        Map<String, Object> cell = switch (column) {
            case DATE -> number(row.date() == null ? null : BigDecimal.valueOf(row.date().toEpochDay() + DAYS_FROM_SHEETS_EPOCH_TO_UNIX_EPOCH));
            case SUPPLIER -> text(row.supplier());
            case OBJECT -> text(row.object());
            case EXPENSE_TYPE -> text(row.expenseType());
            case AMOUNT_BEFORE_TAX -> number(row.amountBeforeTax());
            case GST -> number(row.gst());
            case QST -> number(row.qst());
            case TOTAL -> number(row.total());
            case NOTES -> text(row.notes());
            case PHOTO -> photos(row.photos());
            case ID -> text(row.rowId());
        };
        Map<String, Object> format = switch (column) {
            case DATE -> Map.of("numberFormat", Map.of("type", "DATE", "pattern", "d mmmm yyyy"));
            case AMOUNT_BEFORE_TAX, GST, QST, TOTAL -> Map.of("numberFormat", Map.of("type", "NUMBER", "pattern", "#,##0.00"));
            case ID -> Map.of("backgroundColor", GREY_BACKGROUND, "textFormat", Map.of("foregroundColor", GREY_TEXT));
            default -> Map.of();
        };
        if (format.isEmpty()) {
            return cell;
        }
        Map<String, Object> formatted = new LinkedHashMap<>(cell);
        formatted.put("userEnteredFormat", format);
        return formatted;
    }

    /** L'onglet de la configuration, par son titre exact : absent, il est refusé comme un fichier inaccessible (section 8 du PRD). */
    private JsonNode findTab(String authorization, String fileId, String tab) {
        JsonNode file = calls.call(() -> restClient.get()
                .uri(uri -> uri.path("/v4/spreadsheets/{id}").queryParam("fields", "sheets.properties(sheetId,title,gridProperties.rowCount,gridProperties.columnCount)").build(fileId))
                .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(JsonNode.class));
        if (file == null || !file.path("sheets").isArray()) {
            throw new TransientGoogleException("REPONSE_ILLISIBLE");
        }
        for (JsonNode sheet : file.get("sheets")) {
            if (tab.equals(sheet.at("/properties/title").asString())) {
                return sheet.get("properties");
            }
        }
        throw new BlockedGoogleException(MotifBlocage.FICHIER_INACCESSIBLE);
    }

    /** La ligne qui porte déjà cet identifiant dans cet onglet, où qu'elle ait été déplacée (R-21). */
    private Optional<Integer> findRow(String authorization, String fileId, int sheetId, String rowId) {
        Map<String, Object> lookup = Map.of("dataFilters", List.of(Map.of("developerMetadataLookup",
                Map.of("metadataKey", METADATA_KEY, "metadataValue", rowId))));
        JsonNode result = calls.call(() -> restClient.post().uri("/v4/spreadsheets/{id}/developerMetadata:search", fileId)
                .header(HttpHeaders.AUTHORIZATION, authorization).contentType(MediaType.APPLICATION_JSON).body(lookup).retrieve().body(JsonNode.class));
        if (result == null) {
            throw new TransientGoogleException("REPONSE_ILLISIBLE");
        }
        for (JsonNode match : result.path("matchedDeveloperMetadata")) {
            JsonNode range = match.at("/developerMetadata/location/dimensionRange");
            if (range.path("sheetId").asInt(-1) == sheetId && "ROWS".equals(range.path("dimension").asString()) && range.path("startIndex").isNumber()) {
                return Optional.of(range.get("startIndex").asInt());
            }
        }
        return Optional.empty();
    }

    /**
     * Une ligne vide à sa place, qui reprend la mise en forme de la ligne de données la plus proche, et sa liste déroulante de TYPE : PASTE_FORMAT exclut la validation des données
     * (documentation de Google), qui se colle à part. Au bout de l'onglet, une ligne s'ajoute au lieu de s'insérer.
     */
    private static List<Map<String, Object>> newRow(SheetLayout layout, SheetLayout.Placement placement, int sheetId, int rowCount) {
        int at = placement.insertAt();
        List<Map<String, Object>> requests = new ArrayList<>();
        if (at >= rowCount) {
            requests.add(Map.of("appendDimension", Map.of("sheetId", sheetId, "dimension", "ROWS", "length", 1)));
        } else {
            requests.add(Map.of("insertDimension", Map.of(
                    "range", Map.of("sheetId", sheetId, "dimension", "ROWS", "startIndex", at, "endIndex", at + 1), "inheritFromBefore", true)));
        }
        placement.formatSourceRow().ifPresent(source -> {
            int sourceRow = source < at ? source : source + 1;
            int typeColumn = layout.columnOf(SheetColumn.EXPENSE_TYPE);
            requests.add(copyPaste(sheetId, sourceRow, at, 0, layout.width(), "PASTE_FORMAT"));
            requests.add(copyPaste(sheetId, sourceRow, at, typeColumn, typeColumn + 1, "PASTE_DATA_VALIDATION"));
        });
        return requests;
    }

    private static Map<String, Object> copyPaste(int sheetId, int sourceRow, int destinationRow, int firstColumn, int endColumn, String pasteType) {
        return Map.of("copyPaste", Map.of("source", cells(sheetId, sourceRow, firstColumn, endColumn), "destination", cells(sheetId, destinationRow, firstColumn, endColumn),
                "pasteType", pasteType, "pasteOrientation", "NORMAL"));
    }

    private static Map<String, Object> cells(int sheetId, int row, int firstColumn, int endColumn) {
        return Map.of("sheetId", sheetId, "startRowIndex", row, "endRowIndex", row + 1, "startColumnIndex", firstColumn, "endColumnIndex", endColumn);
    }

    /** Ajoute l'en-tête PHOTO après la dernière colonne (D-07), avec une colonne de plus dans l'onglet s'il n'y a plus de place. Aucun autre en-tête n'est touché. */
    private static void addPhotoColumn(List<Map<String, Object>> requests, SheetLayout layout, int sheetId, int columnCount) {
        int photoColumn = layout.columnOf(SheetColumn.PHOTO);
        if (photoColumn >= columnCount) {
            requests.add(Map.of("appendDimension", Map.of("sheetId", sheetId, "dimension", "COLUMNS", "length", photoColumn + 1 - columnCount)));
        }
        requests.add(Map.of("updateCells", Map.of(
                "start", Map.of("sheetId", sheetId, "rowIndex", layout.headerRow(), "columnIndex", photoColumn),
                "rows", List.of(Map.of("values", List.of(text(SheetColumn.PHOTO.header())))),
                "fields", "userEnteredValue")));
    }

    /** Les valeurs affichées de l'onglet entier, ligne par ligne : l'API ne rend que jusqu'à la dernière ligne garnie. */
    private List<List<String>> readGrid(String authorization, String fileId, String tab) {
        JsonNode values = calls.call(() -> restClient.get().uri("/v4/spreadsheets/{id}/values/{range}", fileId, "'" + tab.replace("'", "''") + "'")
                .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(JsonNode.class));
        if (values == null) {
            throw new TransientGoogleException("REPONSE_ILLISIBLE");
        }
        List<List<String>> grid = new ArrayList<>();
        for (JsonNode line : values.path("values")) {
            List<String> cells = new ArrayList<>();
            for (JsonNode cell : line) {
                cells.add(cell.asString(""));
            }
            grid.add(cells);
        }
        return grid;
    }

    /** Une valeur absente donne une cellule sans valeur, donc vide (R-08). */
    private Map<String, Object> cell(SheetColumn column, SheetRow row) {
        return switch (column) {
            case DATE -> number(row.date() == null ? null : BigDecimal.valueOf(row.date().toEpochDay() + DAYS_FROM_SHEETS_EPOCH_TO_UNIX_EPOCH));
            case SUPPLIER -> text(row.supplier());
            case OBJECT -> text(row.object());
            case EXPENSE_TYPE -> text(row.expenseType());
            case AMOUNT_BEFORE_TAX -> number(row.amountBeforeTax());
            case GST -> number(row.gst());
            case QST -> number(row.qst());
            case TOTAL -> number(row.total());
            case NOTES -> text(row.notes());
            case PHOTO -> photos(row.photos());
        };
    }

    private static Map<String, Object> text(String value) {
        return value == null || value.isEmpty() ? Map.of() : Map.of("userEnteredValue", Map.of("stringValue", value));
    }

    private static Map<String, Object> number(BigDecimal value) {
        return value == null ? Map.of() : Map.of("userEnteredValue", Map.of("numberValue", value));
    }

    /** Un lien par photo dans une seule cellule : une fonction {@code HYPERLINK} n'en porte qu'un (R-27). Le texte entre deux liens n'en porte aucun. */
    private Map<String, Object> photos(List<PhotoLink> links) {
        if (links.isEmpty()) {
            return Map.of();
        }
        StringBuilder text = new StringBuilder();
        List<Map<String, Object>> runs = new ArrayList<>();
        for (PhotoLink link : links) {
            if (!text.isEmpty()) {
                runs.add(Map.of("startIndex", text.length(), "format", Map.of()));
                text.append(PHOTO_SEPARATOR);
            }
            Map<String, Object> run = new LinkedHashMap<>();
            run.put("startIndex", text.length());
            run.put("format", Map.of("link", Map.of("uri", photoLinkTemplate.replace("{id}", UriUtils.encodePathSegment(link.driveFileId(), StandardCharsets.UTF_8)))));
            runs.add(run);
            text.append(link.label());
        }
        // Le séparateur est ajouté avant le lien suivant : son passage sans lien commence où le texte précédent s'arrête.
        return Map.of("userEnteredValue", Map.of("stringValue", text.toString()), "textFormatRuns", runs);
    }
}
