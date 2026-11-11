package ca.gestionchalet.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Corps de {@code PUT /api/recus/{id}} : la version sur laquelle la correction s'appuie et les valeurs complètes ; tout autre champ envoyé par le client est ignoré. */
public record RecuCorrectionDTO(@NotNull @Positive Integer version, @NotNull @Valid ValeursDTO valeurs) {
}
