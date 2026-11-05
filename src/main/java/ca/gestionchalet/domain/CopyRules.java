package ca.gestionchalet.domain;

/**
 * Ce que la copie vers le Sheet exige de la configuration ({@code recus.copie.*}) : un fichier et un onglet (D-62).
 *
 * @param fichier identifiant du fichier du Sheet, vide s'il n'est pas configuré
 * @param onglet nom de l'onglet, vide s'il n'est pas configuré
 */
public record CopyRules(String fichier, String onglet) {

    /** Sans fichier ou sans onglet, aucune copie ne part : ni jeton, ni Drive, ni Sheets. Un texte fait de séparateurs, espace insécable compris, est vide ({@link ReceiptText#isEmpty}) : {@code isBlank} ne le voit pas. */
    public boolean sheetConfigured() {
        return !ReceiptText.isEmpty(fichier) && !ReceiptText.isEmpty(onglet);
    }
}
