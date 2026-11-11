package ca.gestionchalet.service.dto;

import ca.gestionchalet.domain.ReadingResult;
import java.util.List;

/** Objet lecture du contrat (PRD, section 7.1). */
public record RecuReadingDTO(ReadingResult resultat, ValeursDTO valeurs, List<String> champsRejetes) {

    public RecuReadingDTO {
        champsRejetes = List.copyOf(champsRejetes);
    }
}
