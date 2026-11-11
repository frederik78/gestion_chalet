package ca.gestionchalet.domain;

import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Ce que l'application a écrit dans une cellule de l'onglet généré, DATE à PHOTO (R-53) : le texte tel qu'elle l'a écrit, la date en {@code AAAA-MM-JJ}, un montant en dollars, {@code null}
 * pour une cellule vide. Il ne contient aucun numéro d'inscription (R-25), et n'est jamais exposé par l'API.
 *
 * @param recuId le reçu de la ligne
 * @param colonne le rang de la colonne : 0 pour DATE, 9 pour PHOTO
 * @param contenu le contenu écrit
 */
@Embeddable
public record CelluleMemorisee(UUID recuId, int colonne, String contenu) {
}
