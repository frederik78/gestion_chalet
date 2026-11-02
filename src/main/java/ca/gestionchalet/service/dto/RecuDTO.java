package ca.gestionchalet.service.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Objet reçu du contrat (PRD, section 7.1) : aucun jeton, aucun chemin ni identifiant technique de fichier. */
public record RecuDTO(
        UUID id,
        int version,
        ValeursDTO valeurs,
        Long montantTotalLuCents,
        List<AlerteDTO> alertes,
        String etatCopie,
        String motifBlocage,
        Instant prochaineTentative,
        List<PhotoDTO> photos,
        Instant valideLe,
        Instant corrigeLe) {

    public RecuDTO {
        alertes = List.copyOf(alertes);
        photos = List.copyOf(photos);
    }

    public record AlerteDTO(String code, String message, Long ecartCents) {
    }

    public record PhotoDTO(int rang, String format, long taille, String lienDrive) {
    }
}
