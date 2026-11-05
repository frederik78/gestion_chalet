package ca.gestionchalet.domain;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Ce que la copie vers le Sheet lit de la configuration ({@code recus.copie.*}) : la correspondance de l'année d'un reçu vers son fichier et son onglet (D-18). Une entrée sans
 * fichier ou sans onglet n'est pas une entrée : aucun fichier ne s'ouvre hors de la correspondance (PRD, section 7.4), et ni l'une ni l'autre ne bloque les autres années.
 *
 * @param sheets le fichier et l'onglet de chaque année configurée
 */
public record CopyRules(Map<Integer, SheetTarget> sheets) {

    public CopyRules {
        sheets = sheets.entrySet().stream().filter(entry -> entry.getValue().isUsable()).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Sans aucune entrée, aucune copie ne part : ni jeton, ni Drive, ni Sheets (D-62). */
    public boolean anySheetConfigured() {
        return !sheets.isEmpty();
    }

    /** Vide pour une année absente de la correspondance : le reçu est conservé et ses photos sont déposées, mais le Sheet n'est pas appelé (D-18). */
    public Optional<SheetTarget> sheetFor(int year) {
        return Optional.ofNullable(sheets.get(year));
    }
}
