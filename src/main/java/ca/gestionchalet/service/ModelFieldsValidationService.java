package ca.gestionchalet.service;

import ca.gestionchalet.domain.ReadingResult;
import ca.gestionchalet.domain.ReceiptText;
import ca.gestionchalet.domain.RecuReading;
import ca.gestionchalet.domain.Valeurs;
import ca.gestionchalet.service.dto.ValeursDTO;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Valide champ par champ la réponse du modèle, donnée non fiable (R-03, PRD section 7.3). Seuls les champs connus sont lus : le résultat ne vient jamais de la réponse. Les
 * bornes sont celles de la validation d'un reçu, lues sur {@link ValeursDTO} : une valeur proposée ici n'est jamais refusée pour une autre raison au moment de valider.
 */
@Service
public class ModelFieldsValidationService {

    private static final Pattern AMOUNT = Pattern.compile("(\\d{1,7})(?:\\.(\\d{1,2}))?");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final long CENTS_PER_DOLLAR = 100;
    private static final int CENTS_DIGITS = 2;

    private final Validator validator;

    public ModelFieldsValidationService(Validator validator) {
        this.validator = validator;
    }

    public RecuReading validate(Map<String, Object> fields) {
        Reader reader = new Reader(fields);
        Valeurs valeurs = new Valeurs(
                reader.read("date", "date", ModelFieldsValidationService::toDate),
                reader.read("fournisseur", "fournisseur", Optional::of),
                reader.read("objet", "objet", Optional::of),
                reader.read("typeDepense", "typeDepense", Optional::of),
                reader.read("montantAvantTaxes", "montantAvantTaxesCents", ModelFieldsValidationService::toCents),
                reader.read("tps", "tpsCents", ModelFieldsValidationService::toCents),
                reader.read("tvq", "tvqCents", ModelFieldsValidationService::toCents),
                reader.read("montantTotal", "montantTotalCents", ModelFieldsValidationService::toCents),
                reader.read("numeroInscriptionTps", "numeroInscriptionTps", Optional::of),
                reader.read("numeroInscriptionTvq", "numeroInscriptionTvq", Optional::of));
        return new RecuReading(reader.found ? ReadingResult.LU : ReadingResult.ILLISIBLE, valeurs, reader.rejected);
    }

    private static Optional<LocalDate> toDate(String text) {
        if (!DATE.matcher(text).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Sans flottant : les dollars et les cents sont lus séparément. Sept chiffres ne peuvent pas déborder. */
    private static Optional<Long> toCents(String text) {
        Matcher amount = AMOUNT.matcher(text);
        if (!amount.matches()) {
            return Optional.empty();
        }
        String cents = amount.group(2) == null ? "" : amount.group(2);
        return Optional.of(Long.parseLong(amount.group(1)) * CENTS_PER_DOLLAR
                + Long.parseLong("0" + cents + "0".repeat(CENTS_DIGITS - cents.length())));
    }

    private final class Reader {

        private final Map<String, Object> fields;
        private final List<String> rejected = new ArrayList<>();
        private boolean found;

        private Reader(Map<String, Object> fields) {
            this.fields = fields;
        }

        /** Absent, {@code null} ou fait de séparateurs (D-61) : le modèle n'a rien relevé, ce n'est pas un rejet. */
        private <T> T read(String modelName, String property, Function<String, Optional<T>> conversion) {
            Object raw = fields.get(modelName);
            if (raw == null || raw instanceof String text && ReceiptText.isEmpty(text)) {
                return null;
            }
            Optional<T> value = raw instanceof String text ? conversion.apply(ReceiptText.normalize(text)) : Optional.empty();
            if (value.isEmpty() || !validator.validateValue(ValeursDTO.class, property, value.get()).isEmpty()) {
                rejected.add(property);
                return null;
            }
            found = true;
            return value.get();
        }
    }
}
