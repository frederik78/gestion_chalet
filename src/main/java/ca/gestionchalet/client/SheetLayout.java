package ca.gestionchalet.client;

import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.domain.ReceiptText;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * La structure d'un onglet, lue de ses valeurs affichées : où sont les colonnes, repérées par le nom de leur en-tête (R-24). Les en-têtes lus dans le Sheet sont une entrée non fiable :
 * ils sont seulement comparés aux noms attendus, jamais réécrits ni journalisés, et un texte trop long pour être un nom attendu est ignoré sans être comparé.
 * Une structure inattendue lève {@link BlockedGoogleException} {@code STRUCTURE_SHEET} : rien n'est écrit.
 */
final class SheetLayout {

    /** Le plus long nom attendu tient largement ici : une cellule plus longue n'en est pas un. */
    private static final int MAX_LABEL_LENGTH = 100;

    private final int headerRow;
    private final Map<SheetColumn, Integer> columns;
    private final boolean photoColumnToAdd;
    private final int usedRows;

    private SheetLayout(int headerRow, Map<SheetColumn, Integer> columns, boolean photoColumnToAdd, int usedRows) {
        this.headerRow = headerRow;
        this.columns = columns;
        this.photoColumnToAdd = photoColumnToAdd;
        this.usedRows = usedRows;
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
        return new SheetLayout(headerRow, columns, photoColumnToAdd, grid.size());
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

    /** Le rang, à partir de 0, de la première ligne libre. */
    int usedRows() {
        return usedRows;
    }
}
