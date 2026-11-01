package ca.gestionchalet.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de {@code POST /api/connexion}. Le mot de passe n'apparaît jamais dans {@link #toString()}. */
public record ConnexionDTO(@NotBlank @Size(max = 1000) String motDePasse) {

    @Override
    public String toString() {
        return "ConnexionDTO[motDePasse=***]";
    }
}
