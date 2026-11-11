package archfixtures.rules.domain;

/** Violation : un DTO réside dans service/dto ou web/rest/errors. */
public record OutsidePackageDTO(String valeur) {
}
