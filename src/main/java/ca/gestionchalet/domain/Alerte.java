package ca.gestionchalet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Avertissement calculé par le serveur, jamais bloquant, figé avec le reçu à sa validation : un renvoi ne le recalcule pas.
 *
 * @param code nature de l'alerte
 * @param message texte montré à l'utilisateur
 * @param ecartCents écart constaté, pour {@link AlerteCode#ECART_CALCUL} seulement
 */
@Embeddable
public record Alerte(
        @Enumerated(EnumType.STRING) @Column(nullable = false) AlerteCode code,
        @Column(nullable = false) String message,
        Long ecartCents) {
}
