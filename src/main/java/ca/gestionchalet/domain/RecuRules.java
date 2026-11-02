package ca.gestionchalet.domain;

import java.util.List;

/**
 * Règles de validation d'un reçu, tenues en configuration ({@code recus.*}) et jamais en constantes.
 *
 * @param typesDepense liste fermée des types de dépense, dans l'orthographe du Sheet
 * @param montantMaxCents montant le plus élevé accepté, en cents
 */
public record RecuRules(List<String> typesDepense, long montantMaxCents) {

    public RecuRules {
        typesDepense = List.copyOf(typesDepense);
    }
}
