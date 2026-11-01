package ca.gestionchalet.web.rest.errors;

import ca.gestionchalet.security.IdentifiantsInvalidesException;
import ca.gestionchalet.web.rest.errors.ErreurDTO.ChampInvalideDTO;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Traduit toute exception en corps d'erreur générique ; le détail ne quitte jamais le serveur. */
@RestControllerAdvice
public class GestionnaireErreurs {

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    ResponseEntity<ErreurDTO> identifiantsInvalides() {
        return reponse(CodeErreur.IDENTIFIANTS_INVALIDES);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErreurDTO> validation(MethodArgumentNotValidException exception) {
        List<ChampInvalideDTO> champs = exception.getBindingResult().getFieldErrors().stream()
                .map(GestionnaireErreurs::champInvalide)
                .toList();
        return corps(HttpStatus.BAD_REQUEST, ErreurDTO.validation(champs));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErreurDTO> corpsIllisible() {
        return reponse(CodeErreur.REQUETE_INVALIDE);
    }

    private static ChampInvalideDTO champInvalide(FieldError erreur) {
        String code = switch (String.valueOf(erreur.getCode())) {
            case "NotNull", "NotBlank", "NotEmpty" -> "OBLIGATOIRE";
            case "Size", "Min", "Max", "Positive", "PositiveOrZero" -> "BORNE";
            default -> "FORMAT";
        };
        return new ChampInvalideDTO(erreur.getField(), code);
    }

    private static ResponseEntity<ErreurDTO> reponse(CodeErreur code) {
        return corps(HttpStatus.valueOf(code.statut()), ErreurDTO.de(code));
    }

    private static ResponseEntity<ErreurDTO> corps(HttpStatus statut, ErreurDTO corps) {
        return ResponseEntity.status(statut).contentType(MediaType.APPLICATION_JSON).body(corps);
    }
}
