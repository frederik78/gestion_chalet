package ca.gestionchalet.web.rest.errors;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Corps de toute réponse d'erreur : {@code erreur} et {@code code}, avec {@code champs} seulement pour VALIDATION. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorDTO(String erreur, String code, List<InvalidFieldDTO> champs) {

    public ErrorDTO {
        champs = List.copyOf(champs);
    }

    public static ErrorDTO of(ErrorCode code) {
        return new ErrorDTO(code.message(), code.name(), List.of());
    }

    public static ErrorDTO validation(List<InvalidFieldDTO> champs) {
        return new ErrorDTO(ErrorCode.VALIDATION.message(), ErrorCode.VALIDATION.name(), champs);
    }

    /** Un champ refusé et la raison : {@code OBLIGATOIRE}, {@code FORMAT}, {@code BORNE} ou {@code HORS_LISTE}. */
    public record InvalidFieldDTO(String champ, String code) {
    }
}
