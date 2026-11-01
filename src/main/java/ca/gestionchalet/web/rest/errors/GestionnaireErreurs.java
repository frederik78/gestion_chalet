package ca.gestionchalet.web.rest.errors;

import ca.gestionchalet.security.IdentifiantsInvalidesException;
import ca.gestionchalet.security.TropDeTentativesException;
import ca.gestionchalet.web.rest.errors.ErreurDTO.ChampInvalideDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Traduit toute exception en corps d'erreur générique ; le détail ne quitte jamais le serveur. */
@RestControllerAdvice
public class GestionnaireErreurs {

    private static final Logger JOURNAL = LoggerFactory.getLogger(GestionnaireErreurs.class);

    @ExceptionHandler(IdentifiantsInvalidesException.class)
    ResponseEntity<ErreurDTO> identifiantsInvalides() {
        return reponse(CodeErreur.IDENTIFIANTS_INVALIDES);
    }

    @ExceptionHandler(TropDeTentativesException.class)
    ResponseEntity<ErreurDTO> tropDeTentatives(TropDeTentativesException exception) {
        long secondes = exception.restant().plusNanos(999_999_999L).toSeconds();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(secondes))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErreurDTO.de(CodeErreur.TROP_DE_TENTATIVES));
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

    /** Les exceptions de Spring MVC portent leur statut ; tout le reste est une panne, journalisée sans son message. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErreurDTO> autre(Exception exception) {
        if (exception instanceof ErrorResponse reponseFramework) {
            return reponse(CodeErreur.pourStatut(reponseFramework.getStatusCode().value()));
        }
        JOURNAL.error("Erreur interne : {} en {}", exception.getClass().getName(), premiereTrame(exception));
        return reponse(CodeErreur.ERREUR_INTERNE);
    }

    private static String premiereTrame(Exception exception) {
        StackTraceElement[] trames = exception.getStackTrace();
        return trames.length == 0 ? "inconnu" : trames[0].toString();
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
