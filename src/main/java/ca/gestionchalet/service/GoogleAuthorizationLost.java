package ca.gestionchalet.service;

/** Événement synchrone : l'autorisation Google ne permet plus d'appeler Google (révocation, droits retirés, refus), dans la transaction de celui qui le constate (D-60). */
public record GoogleAuthorizationLost() {
}
