package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

/** Les limites de taille de la section 9, aux valeurs par défaut, sur serveur réel. Les corps sont produits à la volée, sans les allouer. */
@RealServerTest
class PhotoSizeRealServerTest {

    private static final long MIO = 1024 * 1024;
    private static final long PHOTO_MAX = 15 * MIO;
    private static final Map<String, Object> TROP_LOURD = Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE");
    private static final byte[] IMAGE = RecuRequests.png();
    private static final byte[] EN_TETE_PHOTO = ascii("--b\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"a.png\"\r\nContent-Type: image/png\r\n\r\n");

    @LocalServerPort
    int port;

    private TestClient client;

    @BeforeEach
    void seConnecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private static InputStream zeros(long nombre) {
        return new InputStream() {
            private long restants = nombre;

            @Override
            public int read() {
                return restants-- > 0 ? 0 : -1;
            }

            @Override
            public int read(byte[] tampon, int debut, int longueur) {
                if (restants <= 0) {
                    return -1;
                }
                int lus = (int) Math.min(longueur, restants);
                Arrays.fill(tampon, debut, debut + lus, (byte) 0);
                restants -= lus;
                return lus;
            }
        };
    }

    private static byte[] ascii(String texte) {
        return texte.getBytes(StandardCharsets.UTF_8);
    }

    /** Une photo : une vraie image, complétée de zéros jusqu'à la taille voulue. */
    private static InputStream partiePhoto(long taille) {
        return new SequenceInputStream(new SequenceInputStream(new SequenceInputStream(
                new ByteArrayInputStream(EN_TETE_PHOTO),
                new ByteArrayInputStream(IMAGE)), zeros(taille - IMAGE.length)), new ByteArrayInputStream(ascii("\r\n")));
    }

    private Reponse envoyer(String chemin, long taillePhoto, int photos, boolean fragmente) {
        byte[] debut = ascii("--b\r\nContent-Disposition: form-data; name=\"recu\"\r\nContent-Type: application/json\r\n\r\n"
                + JsonMapper.builder().build().writeValueAsString(RecuRequests.corps(RecuRequests.valeurs())) + "\r\n");
        byte[] fin = ascii("--b--\r\n");
        Supplier<InputStream> corps = () -> {
            InputStream flux = new ByteArrayInputStream(debut);
            for (int i = 0; i < photos; i++) {
                flux = new SequenceInputStream(flux, partiePhoto(taillePhoto));
            }
            return new SequenceInputStream(flux, new ByteArrayInputStream(fin));
        };
        long longueur = debut.length + fin.length + photos * (EN_TETE_PHOTO.length + taillePhoto + 2);
        HttpRequest.BodyPublisher publication = fragmente
                ? HttpRequest.BodyPublishers.ofInputStream(corps)
                : HttpRequest.BodyPublishers.fromPublisher(HttpRequest.BodyPublishers.ofInputStream(corps), longueur);
        return client.envoyer(client.requete(chemin)
                .header("Content-Type", "multipart/form-data; boundary=b")
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(publication)
                .build());
    }

    @Test
    void ca70_unePhotoDeLaTailleMaximaleEstAcceptee() {
        Reponse reponse = envoyer("/api/recus", PHOTO_MAX, 1, true);

        assertThat(reponse.statut()).isEqualTo(201);
    }

    @ParameterizedTest
    @CsvSource({
        "15728641, 1, true", // la taille maximale + 1 octet
        "15728641, 1, false",
        "41943040, 1, true", // 40 Mio : connexion coupée avant la correction
        "41943040, 1, false",
        "88080384, 1, true", // 84 Mio, au-delà aussi de la requête totale
        "88080384, 1, false",
    })
    void ca70_unePhotoTropLourdeDonne413EnJson(long taille, int photos, boolean fragmente) {
        Reponse reponse = envoyer("/api/recus", taille, photos, fragmente);

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(TROP_LOURD);
    }

    /** Cinq photos de 15 Mio, chacune permise : avec leurs séparateurs, la requête dépasse 75 Mio. */
    @Test
    void ca70_uneRequeteTotaleTropLourdeDonne413EnJson() {
        Reponse reponse = envoyer("/api/recus", PHOTO_MAX, 5, true);

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(TROP_LOURD);
    }

    /** Une route dont les photos sont lues en premier : le chemin de Spring n'est pas celui de {@code @RequestPart} sur une partie JSON. */
    @Test
    void ca70_uneRouteQuiNeLitQueDesPhotosRepondDeMeme() {
        Reponse reponse = envoyer("/api/essai/photos", PHOTO_MAX + 1, 1, true);

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(TROP_LOURD);
    }

    @Test
    void ca70_uneRouteQuiNeLitQueDesPhotosCompteAussiLesParties() {
        Reponse reponse = envoyer("/api/essai/photos", IMAGE.length, 60, true);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Ce reçu compte trop de photos.", "code", "TROP_DE_PHOTOS"));
    }
}
