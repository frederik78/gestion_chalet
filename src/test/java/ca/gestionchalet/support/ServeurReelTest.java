package ca.gestionchalet.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Test sur serveur réel, port aléatoire, avec le mot de passe d'essai et les doubles de {@link ConfigurationEssai}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"recus.securite.condense-mot-de-passe=" + ConfigurationEssai.CONDENSE})
@Import(ConfigurationEssai.class)
public @interface ServeurReelTest {
}
