package ca.gestionchalet.web.rest.errors;

import ca.gestionchalet.domain.IdempotencyKeyConflictException;
import ca.gestionchalet.domain.ImageTooLargeException;
import ca.gestionchalet.domain.MissingPhotoException;
import ca.gestionchalet.domain.TooManyPhotosException;
import ca.gestionchalet.domain.TooManyReadingsException;
import ca.gestionchalet.domain.UnreadableImageException;
import ca.gestionchalet.domain.UnsupportedPhotoFormatException;
import ca.gestionchalet.web.rest.errors.ErrorDTO.InvalidFieldDTO;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.apache.tomcat.util.http.fileupload.impl.FileCountLimitExceededException;
import org.apache.tomcat.util.http.fileupload.impl.SizeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit toute exception en corps d'erreur générique, comme le {@code ExceptionTranslator} de JHipster, à un écart près : le
 * corps est {@code {erreur, code}} et non un Problem Details, et le détail ne quitte jamais le serveur.
 */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionTranslator.class);

    /** Point de sortie unique : seul le statut compte, aucun en-tête d'exception ({@code Allow}) n'est repris, et une panne est journalisée sans son message. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = ErrorCode.fromStatus(statusCode.value());
        if (code == ErrorCode.ERREUR_INTERNE) {
            LOG.error("Erreur interne : {} en {}", ex.getClass().getName(), firstFrame(ex));
        }
        // Même test que super, qui journaliserait ici le message de l'exception : il peut porter un contenu de reçu.
        if (request instanceof ServletWebRequest servletRequest) {
            HttpServletResponse response = servletRequest.getResponse();
            if (response != null && response.isCommitted()) {
                return null;
            }
        }
        HttpHeaders json = new HttpHeaders();
        json.setContentType(MediaType.APPLICATION_JSON);
        Object errorBody = body instanceof ErrorDTO ? body : ErrorDTO.of(code);
        // Un corps déjà construit garde son statut ; sinon le statut est celui du code déduit, pour tout statut du framework.
        int status = body instanceof ErrorDTO ? statusCode.value() : code.status();
        return super.handleExceptionInternal(ex, errorBody, json, HttpStatusCode.valueOf(status), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<InvalidFieldDTO> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(ExceptionTranslator::invalidField)
                .toList();
        return handleExceptionInternal(ex, ErrorDTO.validation(fields), headers, status, request);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleMultipartException(MultipartException ex, WebRequest request) {
        ErrorCode code = codeOf(ex);
        return handleExceptionInternal(ex, ErrorDTO.of(code), new HttpHeaders(), HttpStatusCode.valueOf(code.status()), request);
    }

    /** Spring classe en dépassement de taille toute limite de Tomcat, celle du nombre de parties comprise : la cause décide, ici aussi. */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleMultipartException(ex, request);
    }

    /**
     * Tomcat signale une limite franchie par la cause. Spring ne la reconnaît que si les photos sont lues d'abord : par un {@code @RequestPart}
     * sur une partie JSON, il lève une simple {@link MultipartException} (mesuré sur Spring 7.0.10 et Tomcat 11.0.21).
     */
    private static ErrorCode codeOf(MultipartException ex) {
        if (ex.contains(FileCountLimitExceededException.class)) {
            return ErrorCode.TROP_DE_PHOTOS;
        }
        return ex.contains(SizeException.class) ? ErrorCode.TAILLE_EXCESSIVE : ErrorCode.REQUETE_INVALIDE;
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<Object> handleIdempotencyKeyConflict(IdempotencyKeyConflictException ex, WebRequest request) {
        return handleExceptionInternal(ex, ErrorDTO.of(ErrorCode.CLE_DEJA_UTILISEE), new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(MissingPhotoException.class)
    public ResponseEntity<Object> handleMissingPhoto(MissingPhotoException ex, WebRequest request) {
        return handleExceptionInternal(ex, ErrorDTO.of(ErrorCode.PHOTO_MANQUANTE), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(TooManyPhotosException.class)
    public ResponseEntity<Object> handleTooManyPhotos(TooManyPhotosException ex, WebRequest request) {
        return handleExceptionInternal(ex, ErrorDTO.of(ErrorCode.TROP_DE_PHOTOS), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(UnreadableImageException.class)
    public ResponseEntity<Object> handleUnreadableImage(UnreadableImageException ex, WebRequest request) {
        return handleExceptionInternal(ex, ErrorDTO.of(ErrorCode.IMAGE_ILLISIBLE), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ImageTooLargeException.class)
    public ResponseEntity<Object> handleImageTooLarge(ImageTooLargeException ex, WebRequest request) {
        return handleExceptionInternal(ex, ErrorDTO.of(ErrorCode.IMAGE_TROP_GRANDE), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(UnsupportedPhotoFormatException.class)
    public ResponseEntity<Object> handleUnsupportedPhotoFormat(UnsupportedPhotoFormatException ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
    }

    /** Le point de sortie ne garde aucun en-tête : {@code Retry-After} est ajouté à sa réponse. */
    @ExceptionHandler(TooManyReadingsException.class)
    public ResponseEntity<Object> handleTooManyReadings(TooManyReadingsException ex, WebRequest request) {
        ResponseEntity<Object> response = handleExceptionInternal(ex, ErrorDTO.of(ErrorCode.TROP_DE_TENTATIVES), new HttpHeaders(), HttpStatus.TOO_MANY_REQUESTS, request);
        if (response == null) {
            return null;
        }
        long seconds = ex.retryAfter().plusNanos(999_999_999L).toSeconds();
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds)).body(response.getBody());
    }

    @ExceptionHandler
    public ResponseEntity<Object> handleAnyException(Exception ex, WebRequest request) {
        return handleExceptionInternal(ex, null, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private static String firstFrame(Exception ex) {
        StackTraceElement[] frames = ex.getStackTrace();
        return frames.length == 0 ? "inconnu" : frames[0].toString();
    }

    private static InvalidFieldDTO invalidField(FieldError error) {
        String code = switch (String.valueOf(error.getCode())) {
            case "NotNull", "NotBlank", "NotEmpty" -> "OBLIGATOIRE";
            case "Size", "Min", "Max", "Positive", "PositiveOrZero", "PastOrPresent" -> "BORNE";
            case "ExpenseType" -> "HORS_LISTE";
            default -> "FORMAT";
        };
        // Le contrat nomme le champ lui-même, sans l'objet qui le porte : « date » et non « valeurs.date ».
        String field = error.getField();
        return new InvalidFieldDTO(field.substring(field.lastIndexOf('.') + 1), code);
    }
}
