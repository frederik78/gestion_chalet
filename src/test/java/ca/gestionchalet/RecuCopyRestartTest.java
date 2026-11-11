package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ApiStub;
import ca.gestionchalet.support.ApiStub.Request;
import ca.gestionchalet.support.ApiStub.Response;
import ca.gestionchalet.support.GoogleStub;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.net.URI;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * CA50 et CA99 : l'application complète, arrêtée puis redémarrée sur la même base, avec les vrais adaptateurs de Drive et du Sheet pointés sur un serveur local. Au démarrage,
 * toute photo à déposer est reprise sans action de l'utilisateur, celle dont l'essai attendait son heure comme celle qui attendait l'autorisation, et le Sheet ne reçoit aucune requête.
 */
class RecuCopyRestartTest {

    private static String[] arguments(Path base, GoogleStub google, ApiStub api) {
        return new String[] {
            "--server.port=0",
            "--recus.securite.condense-mot-de-passe=" + TestDoublesConfiguration.CONDENSE,
            "--recus.securite.cookie-securise=false",
            "--recus.copie.automatique=true",
            "--recus.dossier-photos=" + base.resolve("photos"),
            "--recus.copie.annees.2026.fichier=fichier-essai",
            "--recus.copie.annees.2026.onglet=Comptes 2026",
            "--recus.copie.adresse-drive=" + api.address(),
            "--recus.copie.adresse-sheets=" + api.address(),
            "--spring.datasource.url=" + url(base),
            "--recus.google.adresse-consentement=" + google.adresseConsentement(),
            "--recus.google.adresse-jeton=" + google.adresseJeton()};
    }

    private static String url(Path base) {
        return "jdbc:sqlite:" + base.resolve("base.db") + "?foreign_keys=true";
    }

    private static TestClient connecte(ConfigurableApplicationContext application) {
        TestClient client = new TestClient(application.getEnvironment().getRequiredProperty("local.server.port", Integer.class), "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        return client;
    }

    private static void autoriser(TestClient client) {
        String demande = client.get("/api/google/autorisation").enTete("Location");
        String state = UriComponentsBuilder.fromUri(URI.create(demande)).build().getQueryParams().getFirst("state");
        client.get("/api/google/rappel?code=code-temoin&state=" + state);
        assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"VALIDE\"}");
    }

    private static void attendre(String quoi, BooleanSupplier condition) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as(quoi).isLessThan(limite);
            Thread.sleep(25);
        }
    }

    private static Response unDriveVide(ApiStub api, Request requete) {
        if (requete.is("GET", "/drive/v3/files")) {
            return Response.json("{\"files\":[]}");
        }
        if (requete.is("POST", "/drive/v3/files")) {
            return Response.json("{\"id\":\"dossier-drive\"}");
        }
        if (requete.is("POST", "/upload/drive/v3/files")) {
            return Response.json("{\"id\":\"fichier-drive-" + api.requests().stream().filter(r -> r.path().startsWith("/upload")).count() + "\"}");
        }
        return Response.status(404);
    }

    @Test
    void ca50_ca99_auRedemarrageLesDeuxPhotosEnAttenteSontDeposeesSansActionEtSansAppelAuSheet(@TempDir Path base) throws Exception {
        try (GoogleStub google = new GoogleStub(); ApiStub api = new ApiStub()) {
            google.repondreJeton(3600, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
            api.answer(requete -> Response.status(503));
            try (ConfigurableApplicationContext application = new SpringApplicationBuilder(GestionChaletApplication.class).run(arguments(base, google, api))) {
                TestClient client = connecte(application);
                autoriser(client);
                RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png(4));
                attendre("premier essai en panne", () -> !api.requests().isEmpty());
                // Un essai programmé dans une minute : il n'aura pas lieu avant l'arrêt. La seconde photo attend l'autorisation, perdue ici comme par un invalid_grant.
                application.getBean(JdbcTemplate.class).update("update autorisation_google set etat = 'A_RENOUVELER'");
                assertThat(RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png(5)).statut()).isEqualTo(201);
                assertThat(application.getBean(JdbcTemplate.class).queryForObject("select count(*) from photo where drive_file_id is null", Long.class)).isEqualTo(2L);
            }
            // L'autorisation est de nouveau valide au redémarrage (jetons gardés en base) ; Drive répond bien.
            new JdbcTemplate(new DriverManagerDataSource(url(base))).update("update autorisation_google set etat = 'VALIDE'");
            google.reinitialiser();
            api.forget();
            api.answer(requete -> unDriveVide(api, requete));

            try (ConfigurableApplicationContext application = new SpringApplicationBuilder(GestionChaletApplication.class).run(arguments(base, google, api))) {
                JdbcTemplate lignes = application.getBean(JdbcTemplate.class);

                attendre("les deux photos reprises", () -> lignes.queryForObject("select count(drive_file_id) from photo", Long.class) == 2L);

                assertThat(api.requests().stream().filter(requete -> requete.path().equals("/upload/drive/v3/files"))).hasSize(2);
                assertThat(api.requests()).noneSatisfy(requete -> assertThat(requete.path()).contains("spreadsheets"));
            }
        }
    }
}
