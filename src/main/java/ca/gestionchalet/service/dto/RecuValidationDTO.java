package ca.gestionchalet.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Partie {@code recu} de {@code POST /api/recus} : tout autre champ envoyé par le client est ignoré. */
public record RecuValidationDTO(@NotNull UUID cleIdempotence, @NotNull @Valid ValeursDTO valeurs, Long montantTotalLuCents) {
}
