package ca.gestionchalet.service.dto;

import ca.gestionchalet.service.validation.StrictUuidDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Partie {@code recu} de {@code POST /api/recus} : tout autre champ envoyé par le client est ignoré. */
public record RecuValidationDTO(@NotNull @JsonDeserialize(using = StrictUuidDeserializer.class) UUID cleIdempotence, @NotNull @Valid ValeursDTO valeurs, @PositiveOrZero @Max(ValeursDTO.MONTANT_MAX_CENTS) Long montantTotalLuCents) {
}
