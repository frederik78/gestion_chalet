package ca.gestionchalet.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** L'unique compte de l'application : un condensé bcrypt fourni par l'environnement, sans valeur par défaut. */
public final class CompteLocal {

    private static final Pattern BCRYPT = Pattern.compile("\\A\\$2[aby]\\$(\\d{2})\\$[./0-9A-Za-z]{53}\\z");
    private static final int COUT_MINIMAL = 10;

    private final BCryptPasswordEncoder encodeur = new BCryptPasswordEncoder();
    private final String condense;

    public CompteLocal(String condense) {
        if (condense == null || !estFort(condense)) {
            throw new IllegalStateException("Condensé du mot de passe absent ou trop faible : bcrypt de coût "
                    + COUT_MINIMAL + " au moins requis (recus.securite.condense-mot-de-passe).");
        }
        this.condense = condense;
    }

    private static boolean estFort(String condense) {
        Matcher format = BCRYPT.matcher(condense);
        return format.matches() && Integer.parseInt(format.group(1)) >= COUT_MINIMAL;
    }

    public boolean verifier(String motDePasse) {
        return encodeur.matches(motDePasse, condense);
    }
}
