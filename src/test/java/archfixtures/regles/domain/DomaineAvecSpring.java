package archfixtures.regles.domain;

import org.springframework.util.Assert;

/** Violation : tout emprunt du domaine à Spring est une exception nommée. */
public class DomaineAvecSpring {
    void verifier(Object valeur) {
        Assert.notNull(valeur, "valeur");
    }
}
