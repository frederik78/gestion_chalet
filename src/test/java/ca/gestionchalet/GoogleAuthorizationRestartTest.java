package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.service.GoogleTokenService;
import ca.gestionchalet.support.GoogleStub;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/** L'application complète, démarrée pour de bon : l'autorisation survit à un redémarrage, et la clé de chiffrement est exigée. */
class GoogleAuthorizationRestartTest {

    private static String[] arguments(Path base, GoogleStub google, String... autres) {
        String[] communs = {
            "--server.port=0",
            "--recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE,
            "--recus.securite.cookie-securise=false",
            "--spring.datasource.url=jdbc:sqlite:" + base.resolve("base.db") + "?foreign_keys=true",
            "--spring.security.oauth2.client.provider.google.authorization-uri=" + google.adresseConsentement(),
            "--spring.security.oauth2.client.provider.google.token-uri=" + google.adresseJeton()};
        String[] tous = new String[communs.length + autres.length];
        System.arraycopy(communs, 0, tous, 0, communs.length);
        System.arraycopy(autres, 0, tous, communs.length, autres.length);
        return tous;
    }

    private static ConfigurableApplicationContext demarrer(Path base, GoogleStub google) {
        return new SpringApplicationBuilder(GestionChaletApplication.class).run(arguments(base, google));
    }

    private static TestClient connecte(ConfigurableApplicationContext application) {
        TestClient client = new TestClient(application.getEnvironment().getRequiredProperty("local.server.port", Integer.class), "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        return client;
    }

    @Test
    void ca65_apresUnRedemarrageAucuneNouvelleAutorisationNEstDemandee(@TempDir Path base) throws Exception {
        try (GoogleStub google = new GoogleStub()) {
            google.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
            try (ConfigurableApplicationContext application = demarrer(base, google)) {
                TestClient client = connecte(application);
                String demande = client.get("/api/google/autorisation").enTete("Location");
                String state = UriComponentsBuilder.fromUri(URI.create(demande)).build().getQueryParams().getFirst("state");
                client.get("/api/google/rappel?code=code-temoin&state=" + state);
                assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"VALIDE\"}");
            }
            google.reinitialiser();

            try (ConfigurableApplicationContext application = demarrer(base, google)) {
                assertThat(connecte(application).get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"VALIDE\"}");
                // Le jeton d'accès a expiré entre-temps : le jeton de renouvellement, relu et déchiffré, suffit.
                assertThat(application.getBean(GoogleTokenService.class).accessToken()).contains(GoogleStub.JETON_ACCES);
            }

            assertThat(google.requetes()).singleElement().satisfies(requete ->
                    assertThat(requete.parametres()).containsEntry("grant_type", "refresh_token")
                            .containsEntry("refresh_token", GoogleStub.JETON_RENOUVELLEMENT));
        }
    }

    /** Vide comme absente : application.yml n'en a aucune, seule la configuration d'essai en fournit une, que ce test retire. */
    @ParameterizedTest
    @ValueSource(strings = {"", "dHJvcC1jb3VydGU=", "pas de la base 64 !"})
    void cs23_uneCleAbsenteOuInvalideEmpecheLeDemarrage(String cle, @TempDir Path base) throws Exception {
        Files.createDirectories(base);
        try (GoogleStub google = new GoogleStub()) {
            assertThatThrownBy(() -> new SpringApplicationBuilder(GestionChaletApplication.class)
                    .run(arguments(base, google, "--recus.google.cle-chiffrement=" + cle)))
                    .hasRootCauseInstanceOf(IllegalStateException.class)
                    .rootCause().hasMessage("Clé de chiffrement absente ou invalide : 32 octets en base 64 requis (recus.google.cle-chiffrement).");
        }
    }

    @Test
    void cs23_laCleDeLaConfigurationPrincipaleNeVientQueDeLEnvironnement() throws Exception {
        String principale = new String(GoogleAuthorizationRestartTest.class.getResourceAsStream("/application.yml").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(principale).doesNotContain("cle-chiffrement:");
    }
}
