package ca.gestionchalet.service.validation;

import java.util.function.Function;

/**
 * Un texte reçu comme une chaîne JSON, rien d'autre : Jackson 3.1.7 convertit sinon un nombre ou un booléen en texte, et ses règles de
 * coercition ({@code CoercionConfigs}) ne se règlent que par mapper, par type logique ou par classe, jamais par champ ni par DTO.
 */
public final class StrictStringDeserializer extends StrictTextDeserializer<String> {

    public StrictStringDeserializer() {
        super(String.class, "(?s).*", Function.identity());
    }
}
