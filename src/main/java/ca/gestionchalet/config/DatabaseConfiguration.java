package ca.gestionchalet.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Spring Boot configure JPA et les transactions ; il reste à créer le dossier de la base, que SQLite ne crée pas, avant le premier lien. */
@Configuration
public class DatabaseConfiguration {

    @Bean
    static BeanFactoryPostProcessor databaseDirectory(Environment environment) {
        return beanFactory -> {
            try {
                Files.createDirectories(Path.of(environment.getRequiredProperty("recus.dossier-donnees")));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
