package ca.gestionchalet.service.dto;

/** État de l'autorisation Google, seule information exposée à la PWA : aucun jeton. */
public record GoogleAuthorizationStateDTO(String etat) {
}
