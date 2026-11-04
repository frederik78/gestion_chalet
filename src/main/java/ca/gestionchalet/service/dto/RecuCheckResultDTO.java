package ca.gestionchalet.service.dto;

import java.util.List;

/** Réponse de {@code POST /api/recus/controles} : les alertes seules, rien de l'éventuel autre reçu. */
public record RecuCheckResultDTO(List<RecuDTO.AlerteDTO> alertes) {

    public RecuCheckResultDTO {
        alertes = List.copyOf(alertes);
    }
}
