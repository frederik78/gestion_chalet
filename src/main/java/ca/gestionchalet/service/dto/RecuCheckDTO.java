package ca.gestionchalet.service.dto;

import ca.gestionchalet.service.validation.StrictUuidDeserializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Corps de {@code POST /api/recus/controles} : mêmes contrôles qu'à la validation ; tout autre champ envoyé par le client est ignoré.
 *
 * @param recuId en correction, le reçu à écarter de la comparaison : il n'est jamais son propre doublon (R-15)
 */
public record RecuCheckDTO(@NotNull @Valid ValeursDTO valeurs, @PositiveOrZero @Max(ValeursDTO.MONTANT_MAX_CENTS) Long montantTotalLuCents,
        @JsonDeserialize(using = StrictUuidDeserializer.class) UUID recuId) {
}
