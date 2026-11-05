package ca.gestionchalet.domain;

/**
 * Où s'écrit la ligne d'un reçu : un fichier du Sheet et l'un de ses onglets, tels que la configuration les donne pour l'année du reçu (D-18).
 *
 * @param fileId identifiant du fichier du Sheet
 * @param tab titre exact de l'onglet
 */
public record SheetTarget(String fileId, String tab) {

    /** Un fichier et un onglet non vides ; un texte fait de séparateurs, espace insécable compris, est vide ({@link ReceiptText#isEmpty}) : {@code isBlank} ne le voit pas. */
    public boolean isUsable() {
        return !ReceiptText.isEmpty(fileId) && !ReceiptText.isEmpty(tab);
    }
}
