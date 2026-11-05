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
