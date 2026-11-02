package archfixtures.rules.domain;

import org.springframework.util.Assert;

/** Violation : tout emprunt du domaine à Spring est une exception nommée. */
public class DomainWithSpring {
    void verifier(Object valeur) {
        Assert.notNull(valeur, "valeur");
    }
}
