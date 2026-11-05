package ca.gestionchalet.domain;

import java.util.List;

/**
 * Résultat d'une lecture : une simple proposition, jamais enregistrée (D-02).
 *
 * @param resultat issue de la lecture
 * @param valeurs valeurs retenues ; une valeur absente ou rejetée reste {@code null}
 * @param champsRejetes champs de {@code valeurs} dont la valeur proposée a été rejetée
 */
public record RecuReading(ReadingResult resultat, Valeurs valeurs, List<String> champsRejetes) {

    public RecuReading {
        champsRejetes = List.copyOf(champsRejetes);
    }
}
