package ca.gestionchalet.client;

import ca.gestionchalet.client.SheetClient.PhotoLink;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.SheetTarget;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Google Sheets par son API REST (v4), sans bibliothèque cliente (voir {@link GoogleDriveClient}). Une ligne s'écrit en un seul lot, donc tout ou rien : ses cellules et son
 * identifiant invisible, une métadonnée de développeur attachée à la ligne, qui la suit quand d'autres lignes s'insèrent (R-21, Q-07 à confirmer à l'essai réel).
 * Les valeurs s'écrivent par {@code userEnteredValue.stringValue}, jamais par {@code formulaValue} ni par un mode de saisie qui interprète le texte (D-19).
 *
 * <p><b>Limite, jusqu'à la structure du Sheet (001-11) et le placement par mois (001-07) :</b> les colonnes sont celles de l'ordre observé (DATE à PHOTO, de A à J), une nouvelle
 * ligne se place après la dernière ligne garnie de l'onglet, et le fichier et l'onglet viennent de la configuration, sans repérage des en-têtes. Sans fichier ou sans onglet
 * configuré, aucune requête ne part : la copie est bloquée. Ne pas configurer un vrai Sheet avant 001-11.
 */
public class GoogleSheetClient implements SheetClient {

    private static final String METADATA_KEY = "recuId";
    private static final long DAYS_FROM_SHEETS_EPOCH_TO_UNIX_EPOCH = 25569;
    private static final String PHOTO_SEPARATOR = ", ";

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
        int rowIndex = existing.orElseGet(layout::usedRows);
        List<Map<String, Object>> requests = new ArrayList<>();
        if (layout.photoColumnToAdd()) {
            addPhotoColumn(requests, layout, sheetId, tabProperties.at("/gridProperties/columnCount").asInt());
        }
        if (existing.isEmpty() && rowIndex >= tabProperties.at("/gridProperties/rowCount").asInt()) {
            requests.add(Map.of("appendDimension", Map.of("sheetId", sheetId, "dimension", "ROWS", "length", 1)));
        }
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
