package ca.gestionchalet.web.rest.errors;

import ca.gestionchalet.web.rest.errors.ErreurDTO.ChampInvalideDTO;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit toute exception en corps d'erreur générique, comme le {@code ExceptionTranslator} de JHipster, à un écart près : le
 * corps est {@code {erreur, code}} et non un Problem Details, et le détail ne quitte jamais le serveur.
 */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionTranslator.class);

    /** Toutes les exceptions de Spring MVC passent ici : seul leur statut compte, et aucun de leurs en-têtes ({@code Allow}) n'est repris. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        CodeErreur code = CodeErreur.pourStatut(statusCode.value());
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        Object corps = body instanceof ErreurDTO ? body : ErreurDTO.de(code);
        return super.handleExceptionInternal(ex, corps, json, HttpStatusCode.valueOf(code.statut()), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ChampInvalideDTO> champs = ex.getBindingResult().getFieldErrors().stream()
                .map(ExceptionTranslator::champInvalide)
                .toList();
        return handleExceptionInternal(ex, ErreurDTO.validation(champs), headers, status, request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErreurDTO> handleMultipartException() {
        return reponse(CodeErreur.REQUETE_INVALIDE);
    }

    /** Les autres exceptions de Spring portent leur statut ; tout le reste est une panne, journalisée sans son message. */
    @ExceptionHandler
    public ResponseEntity<ErreurDTO> handleAnyException(Exception ex) {
        if (ex instanceof ErrorResponse reponseFramework) {
            return reponse(CodeErreur.pourStatut(reponseFramework.getStatusCode().value()));
        }
        LOG.error("Erreur interne : {} en {}", ex.getClass().getName(), premiereTrame(ex));
        return reponse(CodeErreur.ERREUR_INTERNE);
    }

    private static String premiereTrame(Exception ex) {
        StackTraceElement[] trames = ex.getStackTrace();
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
        return ResponseEntity.status(code.statut()).contentType(MediaType.APPLICATION_JSON).body(ErreurDTO.de(code));
    }
}
