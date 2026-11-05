package ca.gestionchalet.service.validation;

import java.util.UUID;

/** Un UUID écrit sur ses 36 caractères, rien d'autre (section 11.4). */
public final class StrictUuidDeserializer extends StrictTextDeserializer<UUID> {

    public StrictUuidDeserializer() {
        super(UUID.class, "\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}", UUID::fromString);
    }
}
