package ca.gestionchalet.client;

import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.ReceiptText;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * La structure d'un onglet, lue de ses valeurs affichées : où sont les colonnes, repérées par le nom de leur en-tête (R-24). Les en-têtes lus dans le Sheet sont une entrée non fiable :
 * ils sont seulement comparés aux noms attendus, jamais réécrits ni journalisés, et un texte trop long pour être un nom attendu est ignoré sans être comparé.
 * Une structure inattendue lève {@link BlockedGoogleException} {@code STRUCTURE_SHEET} : rien n'est écrit.
 */
final class SheetLayout {

    /** Le plus long nom attendu tient largement ici : une cellule plus longue n'en est pas un. */
    private static final int MAX_LABEL_LENGTH = 100;

    /** Où s'insère une nouvelle ligne, et la ligne de données dont elle reprend la mise en forme, s'il y en a une. */
    record Placement(int insertAt, OptionalInt formatSourceRow) {
    }

    private final List<List<String>> grid;
    private final int headerRow;
    private final Map<SheetColumn, Integer> columns;
    private final boolean photoColumnToAdd;
    private final Map<Month, Integer> titles;

    private SheetLayout(List<List<String>> grid, int headerRow, Map<SheetColumn, Integer> columns, boolean photoColumnToAdd, Map<Month, Integer> titles) {
        this.grid = grid;
        this.headerRow = headerRow;
        this.columns = columns;
        this.photoColumnToAdd = photoColumnToAdd;
        this.titles = titles;
    }

    /** @param grid les valeurs affichées de l'onglet, ligne par ligne, sans les lignes vides de la fin */
    static SheetLayout read(List<List<String>> grid) {
        int headerRow = findHeaderRow(grid);
        Map<SheetColumn, Integer> columns = new EnumMap<>(SheetColumn.class);
        List<String> headers = grid.get(headerRow);
        for (SheetColumn column : SheetColumn.values()) {
            columns.put(column, indexOf(headers, column));
        }
        // PHOTO seule peut manquer : elle s'ajoute après la dernière colonne (D-07, R-24).
        boolean photoColumnToAdd = columns.get(SheetColumn.PHOTO) < 0;
        if (photoColumnToAdd) {
            columns.put(SheetColumn.PHOTO, lastHeader(headers) + 1);
        }
        return new SheetLayout(grid, headerRow, columns, photoColumnToAdd, readTitles(grid, headerRow));
    }

    /**
     * Les titres de mois sous les en-têtes : une ligne dont la seule cellule non vide, dans la première colonne, est un nom de mois. Deux sections d'un même mois ne se départagent pas
     * (R-24). La forme des titres n'a pas été observée de près (PRD, section 2.2) ; toute autre ligne n'est jamais un titre.
     */
    private static Map<Month, Integer> readTitles(List<List<String>> grid, int headerRow) {
        Map<Month, Integer> titles = new EnumMap<>(Month.class);
        for (int row = headerRow + 1; row < grid.size(); row++) {
            List<String> cells = grid.get(row);
            if (cells.isEmpty() || cells.subList(1, cells.size()).stream().anyMatch(cell -> !ReceiptText.isEmpty(cell))) {
                continue;
            }
            for (Month month : Month.values()) {
                if (matches(cells.getFirst(), month.getDisplayName(TextStyle.FULL, Locale.CANADA_FRENCH))) {
                    if (titles.putIfAbsent(month, row) != null) {
                        throw structure();
                    }
                }
            }
        }
        return titles;
    }

    private static int lastHeader(List<String> headers) {
        int last = -1;
        for (int index = 0; index < headers.size(); index++) {
            if (!ReceiptText.isEmpty(headers.get(index))) {
                last = index;
            }
        }
        return last;
    }

    /** La première ligne qui porte un des en-têtes attendus : les titres de mois et les lignes de données n'en portent aucun. */
    private static int findHeaderRow(List<List<String>> grid) {
        for (int row = 0; row < grid.size(); row++) {
            for (String cell : grid.get(row)) {
                for (SheetColumn column : SheetColumn.values()) {
                    if (matches(cell, column.header())) {
                        return row;
                    }
                }
            }
        }
        throw structure();
    }

    /** L'unique colonne de ce nom, ou -1 pour PHOTO absente ; toute autre absente ou une colonne en double, la structure est inattendue (on ne devine pas laquelle écrire). */
    private static int indexOf(List<String> headers, SheetColumn column) {
        int found = -1;
        for (int index = 0; index < headers.size(); index++) {
            if (matches(headers.get(index), column.header())) {
                if (found >= 0) {
                    throw structure();
                }
                found = index;
            }
        }
        if (found < 0 && column != SheetColumn.PHOTO) {
            throw structure();
        }
        return found;
    }

    static boolean matches(String cell, String expected) {
        return cell.length() <= MAX_LABEL_LENGTH && ReceiptText.sameLabel(cell, expected);
    }

    static BlockedGoogleException structure() {
        return new BlockedGoogleException(MotifBlocage.STRUCTURE_SHEET);
    }

    int headerRow() {
        return headerRow;
    }

    int columnOf(SheetColumn column) {
        return columns.get(column);
    }

    /** Vrai si l'en-tête PHOTO manque : l'application l'écrit à {@link #columnOf} avant la ligne (D-07). */
    boolean photoColumnToAdd() {
        return photoColumnToAdd;
    }

    /** Le nombre de colonnes que la ligne écrite couvre : de la première colonne du Sheet à la dernière colonne écrite. */
    int width() {
        return columns.values().stream().mapToInt(Integer::intValue).max().orElseThrow() + 1;
    }

    /**
     * Placement simple dans la section du mois : juste après la dernière ligne garnie de la section, qui court jusqu'au titre suivant ; les lignes vides qui la séparent de la suivante
     * restent en dessous. Une section sans ligne reçoit la sienne juste après son titre. La mise en forme est celle de la ligne de données la plus proche, au-dessus d'abord (section 7.4).
     * Le placement selon la date est celui de 001-07 (R-22) ; tant que les sections ne se créent pas (R-23), un mois sans section bloque aussi.
     */
    Placement placementFor(Month month) {
        Integer title = titles.get(month);
        if (title == null) {
            throw structure();
        }
        int sectionEnd = titles.values().stream().filter(row -> row > title).min(Integer::compare).orElse(grid.size());
        int last = title;
        for (int row = sectionEnd - 1; row > title; row--) {
            if (!isBlank(row)) {
                last = row;
                break;
            }
        }
        return new Placement(last + 1, nearestDataRow(last + 1));
    }

    private OptionalInt nearestDataRow(int insertAt) {
        for (int row = insertAt - 1; row > headerRow; row--) {
            if (isData(row)) {
                return OptionalInt.of(row);
            }
        }
        for (int row = insertAt; row < grid.size(); row++) {
            if (isData(row)) {
                return OptionalInt.of(row);
            }
        }
        return OptionalInt.empty();
    }

    private boolean isData(int row) {
        return !isBlank(row) && !titles.containsValue(row);
    }

    private boolean isBlank(int row) {
        return grid.get(row).stream().allMatch(ReceiptText::isEmpty);
    }
}
