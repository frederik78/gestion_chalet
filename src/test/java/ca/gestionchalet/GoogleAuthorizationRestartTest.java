package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.service.GoogleTokenService;
import ca.gestionchalet.support.GoogleStub;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/** L'application complète, démarrée pour de bon : l'autorisation survit à un redémarrage, et la clé de chiffrement est exigée. */
@ExtendWith(OutputCaptureExtension.class)
class GoogleAuthorizationRestartTest {

    private static final String AUTRE_CLE = "dW5lLWF1dHJlLWNsZS1kZS10cmVudGUtZGV1eC1vY3Q=";

    private static String[] arguments(Path base, GoogleStub google, String... autres) {
        String[] communs = {
            "--server.port=0",
            "--recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE,
            "--recus.securite.cookie-securise=false",
            "--spring.datasource.url=jdbc:sqlite:" + base.resolve("base.db") + "?foreign_keys=true",
            "--recus.google.adresse-consentement=" + google.adresseConsentement(),
            "--recus.google.adresse-jeton=" + google.adresseJeton()};
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

    private static ConfigurableApplicationContext demarrerAvec(Path base, GoogleStub google, String... autres) {
        return new SpringApplicationBuilder(GestionChaletApplication.class).run(arguments(base, google, autres));
    }

    private static String autoriser(TestClient client) {
        String demande = client.get("/api/google/autorisation").enTete("Location");
        String state = UriComponentsBuilder.fromUri(URI.create(demande)).build().getQueryParams().getFirst("state");
        client.get("/api/google/rappel?code=code-temoin&state=" + state);
        return client.get("/api/google/etat").corps();
    }

    private static TestClient.Reponse valider(TestClient client) {
        return RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png());
    }

    /** D-42 : un seul identifiant manquant suffit, l'application démarre et rien ne part vers Google. */
    @ParameterizedTest
    @CsvSource({"'',''", "client-essai,''", "'',secret-essai"})
    void ca64_sansIdentifiantsLApplicationDemarreEtResteABSENTE(String id, String secret, @TempDir Path base) throws Exception {
        try (GoogleStub google = new GoogleStub();
                ConfigurableApplicationContext application = demarrerAvec(base, google,
                        "--recus.google.client-id=" + id, "--recus.google.client-secret=" + secret)) {
            TestClient client = connecte(application);

            assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"ABSENTE\"}");
            TestClient.Reponse recu = valider(client);
            assertThat(recu.statut()).isEqualTo(201);
            assertThat(recu.json()).doesNotContainKeys("etatCopie", "motifBlocage", "prochaineTentative");

            TestClient.Reponse demande = client.get("/api/google/autorisation");
            assertThat(demande.statut()).isEqualTo(503);
            assertThat(demande.enTete("Location")).isNull();
            assertThat(demande.json()).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                    "erreur", "L'autorisation Google n'est pas configurée.", "code", "GOOGLE_NON_CONFIGURE"));

            TestClient.Reponse rappel = client.get("/api/google/rappel?code=code-pirate&state=faux");
            assertThat(rappel.statut()).isEqualTo(302);
            assertThat(rappel.enTete("Location")).isEqualTo("/");
            assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"ABSENTE\"}");
            assertThat(application.getBean(GoogleTokenService.class).accessToken()).isEmpty();

            TestClient anonyme = new TestClient(application.getEnvironment().getRequiredProperty("local.server.port", Integer.class), "source-" + UUID.randomUUID());
            assertThat(anonyme.get("/api/google/autorisation").statut()).isEqualTo(401);
            assertThat(anonyme.get("/api/google/rappel?code=c&state=s").statut()).isEqualTo(401);
            assertThat(google.requetes()).isEmpty();
        }
    }

    /** Une autorisation déjà donnée ne sert plus sans identifiants : l'état le dit, et le retour des identifiants la rend utilisable, sans nouveau consentement. */
    @Test
    void ca64_desIdentifiantsRetiresRendentLEtatABSENTESansEffacerLAutorisation(@TempDir Path base) throws Exception {
        try (GoogleStub google = new GoogleStub()) {
            try (ConfigurableApplicationContext application = demarrer(base, google)) {
                assertThat(autoriser(connecte(application))).isEqualTo("{\"etat\":\"VALIDE\"}");
            }
            google.reinitialiser();
            try (ConfigurableApplicationContext application = demarrerAvec(base, google, "--recus.google.client-id=", "--recus.google.client-secret=")) {
                TestClient client = connecte(application);
                assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"ABSENTE\"}");
                assertThat(application.getBean(GoogleTokenService.class).accessToken()).isEmpty();
            }
            try (ConfigurableApplicationContext application = demarrer(base, google)) {
                assertThat(connecte(application).get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"VALIDE\"}");
            }
            assertThat(google.requetes()).isEmpty();
        }
    }

    /** D-42 : comme CA66, sans perte, sans réutiliser le jeton illisible, sans détail du chiffrement au journal. */
    @Test
    void ca66_uneCleChangeePasseARenouvelerEtLaissePhotosEnAttenteLesDepotsAFaire(@TempDir Path base, CapturedOutput journal) throws Exception {
        try (GoogleStub google = new GoogleStub()) {
            String recu;
            try (ConfigurableApplicationContext application = demarrer(base, google)) {
                TestClient client = connecte(application);
                autoriser(client);
                recu = (String) valider(client).json().get("id");
            }
            google.reinitialiser();
            int debut = journal.getOut().length();

            try (ConfigurableApplicationContext application = demarrerAvec(base, google, "--recus.google.cle-chiffrement=" + AUTRE_CLE)) {
                TestClient client = connecte(application);

                assertThat(application.getBean(GoogleTokenService.class).accessToken()).isEmpty();

                assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"A_RENOUVELER\"}");
                JdbcTemplate lignes = application.getBean(JdbcTemplate.class);
                assertThat(lignes.queryForList("select drive_file_id from photo where recu_id = ?", String.class, recu)).containsOnlyNulls().hasSize(1);
                assertThat(lignes.queryForMap("select * from autorisation_google"))
                        .containsEntry("jeton_acces_chiffre", null).containsEntry("jeton_renouvellement_chiffre", null);
                assertThat(valider(client).statut()).isEqualTo(201);
            }
            assertThat(google.requetes()).isEmpty();
            assertThat(journal.getOut().substring(debut)).doesNotContainIgnoringCase("cipher").doesNotContainIgnoringCase("padding")
                    .doesNotContainIgnoringCase("tag mismatch").contains("Autorisation Google : A_RENOUVELER");
        }
    }
}
