package ca.gestionchalet.security;

/** Un jeton conservé ne se déchiffre plus, par exemple après un changement de clé. */
public class UnreadableTokenException extends RuntimeException {

    private static final long serialVersionUID = 1L;
}
