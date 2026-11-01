package ca.gestionchalet.web.rest.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Corps de toute réponse d'erreur : {@code erreur} et {@code code}, avec {@code champs} seulement pour VALIDATION. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErreurDTO(String erreur, String code, List<ChampInvalideDTO> champs) {

    public ErreurDTO {
        champs = List.copyOf(champs);
    }

    public static ErreurDTO de(CodeErreur code) {
        return new ErreurDTO(code.message(), code.name(), List.of());
    }

    public static ErreurDTO validation(List<ChampInvalideDTO> champs) {
        return new ErreurDTO(CodeErreur.VALIDATION.message(), CodeErreur.VALIDATION.name(), champs);
    }

    /** Un champ refusé et la raison : {@code OBLIGATOIRE}, {@code FORMAT}, {@code BORNE} ou {@code HORS_LISTE}. */
    public record ChampInvalideDTO(String champ, String code) {
    }
}
