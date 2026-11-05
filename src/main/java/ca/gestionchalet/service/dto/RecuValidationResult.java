package ca.gestionchalet.service.dto;

/** Le reçu rendu par une validation, et s'il vient d'être créé. */
public record RecuValidationResult(RecuDTO recu, boolean created) {
}
