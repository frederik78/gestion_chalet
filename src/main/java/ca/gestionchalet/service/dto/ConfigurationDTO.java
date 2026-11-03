package ca.gestionchalet.service.dto;

import java.util.List;

/** Réponse de {@code GET /api/configuration} : ce que les écrans doivent connaître des règles. */
public record ConfigurationDTO(List<String> typesDepense, int photosParRecuMax, long tailleParPhotoMaxOctets, List<String> formats) {

    public ConfigurationDTO {
        typesDepense = List.copyOf(typesDepense);
        formats = List.copyOf(formats);
    }
}
