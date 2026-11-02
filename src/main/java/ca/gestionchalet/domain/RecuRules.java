package ca.gestionchalet.domain;

import java.util.List;

/**
 * Règles de validation d'un reçu, tenues en configuration ({@code recus.*}) et jamais en constantes.
 *
 * @param typesDepense liste fermée des types de dépense, dans l'orthographe du Sheet
 * @param photosParRecuMax nombre maximal de photos d'un reçu, annoncé à la PWA
 * @param tailleParPhotoMaxOctets taille maximale d'une photo, annoncée à la PWA
 * @param formats types de média acceptés pour une photo, annoncés à la PWA
 */
public record RecuRules(
        List<String> typesDepense,
        int photosParRecuMax,
        long tailleParPhotoMaxOctets,
        List<String> formats) {

    public RecuRules {
        typesDepense = List.copyOf(typesDepense);
        formats = List.copyOf(formats);
    }
}
