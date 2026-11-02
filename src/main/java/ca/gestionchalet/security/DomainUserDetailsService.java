package ca.gestionchalet.security;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * L'unique compte de l'application : un condensé bcrypt fourni par l'environnement, sans valeur par défaut. La connexion ne
 * demande aucun identifiant : le nom reçu est donc ignoré. {@code @Component} et non {@code @Service} comme JHipster :
 * {@code ReglesAgentsTest} réserve {@code @Service} au paquet {@code service}.
 */
@Component("userDetailsService")
public final class DomainUserDetailsService implements UserDetailsService {

    private static final Pattern BCRYPT = Pattern.compile("\\A\\$2[aby]\\$(\\d{2})\\$[./0-9A-Za-z]{53}\\z");
    private static final int COUT_MINIMAL = 10;
    private static final int COUT_MAXIMAL = 31;
    private static final String PROPRIETAIRE = "proprietaire";

    private final String condense;

    public DomainUserDetailsService(@Value("${recus.securite.condense-mot-de-passe:}") String condense) {
        if (condense == null || !estFort(condense)) {
            throw new IllegalStateException("Condensé du mot de passe absent ou trop faible : bcrypt de coût "
                    + COUT_MINIMAL + " à " + COUT_MAXIMAL + " requis (recus.securite.condense-mot-de-passe).");
        }
        this.condense = condense;
    }

    private static boolean estFort(String condense) {
        Matcher format = BCRYPT.matcher(condense);
        if (!format.matches()) {
            return false;
        }
        int cout = Integer.parseInt(format.group(1));
        return cout >= COUT_MINIMAL && cout <= COUT_MAXIMAL;
    }

    /** Un nouvel objet à chaque appel : Spring Security efface le mot de passe de celui qu'il a reçu. */
    @Override
    public UserDetails loadUserByUsername(String nomIgnore) {
        return new User(PROPRIETAIRE, condense, List.of());
    }
}
