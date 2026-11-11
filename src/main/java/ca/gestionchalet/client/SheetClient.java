package ca.gestionchalet.client;

import ca.gestionchalet.domain.SheetTarget;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Frontière de Google Sheets (ADR 0001) : la ligne d'un reçu dans l'onglet de la copie. */
public interface SheetClient {

    /**
     * Écrit la ligne dans l'onglet de {@code target} et nulle part ailleurs : si une ligne porte déjà l'identifiant {@link SheetRow#rowId()}, elle est mise à jour, sinon une ligne est créée (R-21). Les valeurs sont écrites
     * telles quelles, jamais interprétées comme une formule (D-19).
     *
     * @throws TransientGoogleException panne passagère : l'appelant réessaiera
     * @throws BlockedGoogleException refus qui attend une action de l'utilisateur
     */
    void write(SheetTarget target, SheetRow row);

    /**
     * Les onglets du fichier, rien d'autre : ni leur contenu, ni celui d'un autre fichier (R-41).
     *
     * @throws BlockedGoogleException fichier absent ou refusé, ou autorisation manquante
     * @throws TransientGoogleException panne de Google ou du réseau
     */
    List<SheetTab> tabs(String fileId);

    /**
     * Crée l'onglet {@code sheetId} ({@code title}) dans le fichier ; ses cellules sont vides.
     *
     * @param sheetId identifiant choisi par l'appelant, qui peut ainsi le noter avant la demande
     */
    void addTab(String fileId, int sheetId, String title);

    /**
     * Écrit tout l'onglet {@code sheetId}, que {@link #addTab} vient de créer : la ligne d'en-têtes (section 7.4 du PRD) puis une ligne par élément de {@code rows}, dans cet ordre.
     * Les valeurs sont écrites telles quelles, jamais interprétées comme une formule (D-19, CS16) ; le numéro d'inscription n'y figure pas (R-25).
     */
    void fill(String fileId, int sheetId, List<SheetRow> rows);

    /**
     * Remplace le contenu de l'onglet {@code targetSheetId} par celui de l'onglet {@code sourceSheetId}, en une seule demande : tout ou rien. L'onglet cible garde son identité ; il est créé
     * s'il n'existe pas, et reprend le titre donné s'il en portait un autre (R-40).
     *
     * @param rowCount nombre de lignes de l'onglet source, en-têtes comprises
     */
    void replace(String fileId, int sourceSheetId, int targetSheetId, String targetTitle, boolean createTarget, int rowCount);

    /** Retire l'onglet {@code sheetId}, et lui seul. */
    void deleteTab(String fileId, int sheetId);

    /**
     * Les valeurs d'une ligne, rien d'autre : les numéros d'inscription n'y figurent pas (D-03, R-25). Un montant absent est {@code null} et donne une cellule vide (R-08).
     *
     * @param rowId identifiant invisible de la ligne, celui du reçu (R-21)
     * @param notes textes des alertes séparés par {@code " ; "}, vide sans alerte (R-26)
     * @param photos un lien par photo, dans une seule cellule (R-27)
     */
    record SheetRow(String rowId, LocalDate date, String supplier, String object, String expenseType, BigDecimal amountBeforeTax,
            BigDecimal gst, BigDecimal qst, BigDecimal total, String notes, List<PhotoLink> photos) {

        public SheetRow {
            photos = List.copyOf(photos);
        }
    }

    /** Un lien de la cellule PHOTO : son texte (« Photo 1 ») et le fichier Drive qu'il vise. */
    record PhotoLink(String label, String driveFileId) {
    }
}
