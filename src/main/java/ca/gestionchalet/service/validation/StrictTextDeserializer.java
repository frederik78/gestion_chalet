package ca.gestionchalet.service.validation;

import java.time.DateTimeException;
import java.util.function.Function;
import java.util.regex.Pattern;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Lit une valeur uniquement depuis un texte au format exact de la section 11.4. Écrit à la main faute de réglage dans Jackson 3.1.7 :
 * mesuré, {@code @JsonFormat(lenient = FALSE)} laisse passer {@code [2026,10,11]}, un signe et des espaces autour de la date, et
 * {@code UUIDDeserializer} accepte le Base64 sans option pour le refuser.
 */
abstract class StrictTextDeserializer<T> extends ValueDeserializer<T> {

    private final Class<T> type;
    private final Pattern format;
    private final Function<String, T> parser;

    StrictTextDeserializer(Class<T> type, String format, Function<String, T> parser) {
        this.type = type;
        this.format = Pattern.compile(format);
        this.parser = parser;
    }

    @Override
    public T deserialize(JsonParser json, DeserializationContext context) {
        if (!json.hasToken(JsonToken.VALUE_STRING)) {
            return type.cast(context.handleUnexpectedToken(type, json));
        }
        String text = json.getString();
        if (format.matcher(text).matches()) {
            try {
                return parser.apply(text);
            } catch (IllegalArgumentException | DateTimeException e) {
                return type.cast(context.handleWeirdStringValue(type, text, "valeur impossible"));
            }
        }
        return type.cast(context.handleWeirdStringValue(type, text, "format inattendu"));
    }
}
