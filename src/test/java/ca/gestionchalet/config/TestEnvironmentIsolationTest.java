package ca.gestionchalet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde du correctif « les tests ne dépendent pas du terminal de celui qui les lance ». Une variable d'environnement passe avant
 * les fichiers de configuration : un {@code .env} chargé dans le terminal changeait le client Google ou le dossier de données
 * des tests. Surefire retire ces variables de la JVM des tests (propriété {@code excludedEnvironmentVariables} du pom, qui n'accepte
 * que des noms exacts) ; ce test échoue si une variable du projet, listée ou oubliée dans cette liste, y arrive quand même.
 */
class TestEnvironmentIsolationTest {

    @Test
    @DisplayName("la JVM des tests n'hérite d'aucune variable RECUS_* ni de la clé du modèle (correctif d'isolation de l'environnement)")
    void testJvmInheritsNoProjectEnvironmentVariable() {
        List<String> inherited = System.getenv().keySet().stream()
                .filter(name -> name.startsWith("RECUS_") || name.equals("ANTHROPIC_API_KEY"))
                .sorted()
                .toList();

        assertThat(inherited).isEmpty();
    }
}
