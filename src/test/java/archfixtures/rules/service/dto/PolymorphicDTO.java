package archfixtures.rules.service.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Violation : aucune désérialisation polymorphe. */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public record PolymorphicDTO(String valeur) {
}
