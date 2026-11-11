package ca.gestionchalet.domain;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ce que la régénération du Sheet lit de la configuration ({@code recus.copie.*}) : le fichier de chaque année (D-18) et les noms de l'onglet généré et de l'onglet temporaire
 * (D-66, D-68). Une année sans fichier n'est pas une entrée : aucun fichier ne s'ouvre hors de la correspondance (R-41).
 *
 * @param files le fichier de chaque année configurée
 * @param generatedTab titre de l'onglet généré
 * @param temporaryTabSuffix suffixe du titre de l'onglet temporaire
 */
public record RegenerationRules(Map<Integer, String> files, String generatedTab, String temporaryTabSuffix) {

    public RegenerationRules {
        files = Map.copyOf(files.entrySet().stream().filter(entry -> !ReceiptText.isEmpty(entry.getValue())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /** Le titre de l'onglet où s'écrit d'abord le nouveau contenu (R-40). */
    public String temporaryTab() {
        return generatedTab + temporaryTabSuffix;
    }
}
