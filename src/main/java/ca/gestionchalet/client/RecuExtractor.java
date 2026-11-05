package ca.gestionchalet.client;

import java.util.List;
import java.util.Map;

/** Frontière du modèle de lecture (ADR 0001). Sa réponse n'est qu'une donnée non fiable, que l'appelant valide champ par champ (R-03). */
public interface RecuExtractor {

    /**
     * Un seul appel au modèle, avec toutes les images du reçu (R-01), sans nouvel essai.
     *
     * @param images copies JPEG, sans métadonnées
     * @param expenseTypes liste fermée des types de dépense
     * @return les champs renvoyés, tels quels : un texte, {@code null} ou toute autre valeur
     * @throws RecuExtractionException service indisponible, délai dépassé ou réponse qui n'est pas un objet JSON
     */
    Map<String, Object> extract(List<byte[]> images, List<String> expenseTypes);
}
