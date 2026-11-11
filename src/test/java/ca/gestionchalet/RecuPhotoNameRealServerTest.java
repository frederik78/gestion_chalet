package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.config.ApplicationProperties;
import ca.gestionchalet.service.RecuCopyService;
import ca.gestionchalet.support.InMemoryGoogle;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** CS12 de bout en bout : un nom de fichier du client n'atteint ni le stockage local, ni l'API, ni Drive, et aucune URL statique ne sert la photo. */
@RealServerTest
class RecuPhotoNameRealServerTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationProperties properties;

    @Autowired
    JdbcTemplate base;

    @Autowired
    RecuCopyService copie;

    @Autowired
    InMemoryGoogle google;

    private static List<Path> fichiersSous(Path dossier) throws IOException {
        try (Stream<Path> chemins = Files.walk(dossier)) {
            return chemins.filter(Files::isRegularFile).toList();
        }
    }

    @Test
    void cs12_unePhotoNommeeAvecUnCheminRemonteNeLaisseSonNomNiDansLeStockageNiDansLApiNiDansDriveNiSousUneUrlStatique() throws IOException {
        base.update("delete from autorisation_google");
        base.update("insert into autorisation_google (id, etat, jeton_acces_chiffre, jeton_acces_expire_le, jeton_renouvellement_chiffre) "
                + "values (1, 'VALIDE', 'acces', '2999-01-01T00:00:00Z', 'renouvellement')");
        google.reset();
        TestClient client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        Path racine = properties.dossierPhotos().toAbsolutePath().normalize();

        Reponse reponse = RecuRequests.validerDeclare(client, RecuRequests.corps(RecuRequests.valeurs()), "image/png", "../../secret.jpg", RecuRequests.png());

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.corps()).doesNotContain("secret");
        UUID recu = UUID.fromString((String) reponse.json().get("id"));
        UUID photo = UUID.fromString(base.queryForObject("select id from photo where recu_id = ?", String.class, recu));
        assertThat(racine.resolve(photo + ".png")).isRegularFile();
        assertThat(fichiersSous(racine.getParent())).noneSatisfy(fichier -> assertThat(fichier.getFileName().toString()).containsIgnoringCase("secret"));
        assertThat(fichiersSous(racine)).allSatisfy(fichier -> assertThat(fichier.getFileName().toString()).matches("[0-9a-f-]{36}\\.(png|jpg)"));

        copie.processPending();

        assertThat(google.fileOf(photo).name()).doesNotContain("secret").doesNotContain("..").endsWith(photo + ".png");
        for (String chemin : List.of("/secret.jpg", "/photos/secret.jpg", "/" + photo + ".png", "/photos/" + photo + ".png")) {
            assertThat(client.get(chemin).statut()).as(chemin).isEqualTo(404);
        }
        // Tomcat rejette un séparateur encodé avant toute ressource : refusé (400) ou introuvable (404), jamais servi.
        for (String chemin : List.of("/..%2F..%2Fsecret.jpg", "/%2e%2e/%2e%2e/secret.jpg")) {
            assertThat(client.get(chemin).statut()).as(chemin).isIn(400, 404);
        }
    }
}
