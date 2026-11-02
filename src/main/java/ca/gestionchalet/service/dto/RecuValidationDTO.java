package ca.gestionchalet.service.dto;

import java.util.UUID;

/** Partie {@code recu} de {@code POST /api/recus} : tout autre champ envoyé par le client est ignoré. */
public record RecuValidationDTO(UUID cleIdempotence, ValeursDTO valeurs, Long montantTotalLuCents) {
}
