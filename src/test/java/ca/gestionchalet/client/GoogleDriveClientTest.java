package ca.gestionchalet.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.client.DriveClient.DrivePhoto;
import ca.gestionchalet.domain.MotifBlocage;
import ca.gestionchalet.support.ApiStub;
import ca.gestionchalet.support.ApiStub.Request;
import ca.gestionchalet.support.ApiStub.Response;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.client.RestClient;

/** L'adaptateur de Drive contre un serveur local : le format exact des requêtes, la recherche avant la création, le classement des pannes. */
class GoogleDriveClientTest {

    private static final String DOSSIER = "Reçus du chalet";
    private static final UUID PHOTO = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final byte[] OCTETS = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    private ApiStub drive;
    private Supplier<Optional<String>> jetons = () -> Optional.of("jeton-d-essai");

    @BeforeEach
    void demarrer() throws IOException {
        drive = new ApiStub();
    }

    @AfterEach
    void arreter() {
        drive.close();
    }

    private GoogleDriveClient client() {
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrique.setReadTimeout(Duration.ofSeconds(3));
        return new GoogleDriveClient(RestClient.builder().requestFactory(fabrique).baseUrl(drive.address()).build(), () -> jetons.get(), DOSSIER);
    }

    private static DrivePhoto photo() {
        return new DrivePhoto(PHOTO, "2026-10-11_" + PHOTO + ".png", "image/png", OCTETS);
    }

    /** Un Drive vide : ni dossier, ni fichier ; chaque création rend un identifiant. */
    private void driveVide() {
        drive.answer(requete -> {
            if (requete.is("GET", "/drive/v3/files")) {
                return Response.json("{\"files\":[]}");
            }
            if (requete.is("POST", "/drive/v3/files")) {
                return Response.json("{\"id\":\"dossier-drive-1\"}");
            }
            if (requete.is("POST", "/upload/drive/v3/files")) {
                return Response.json("{\"id\":\"fichier-drive-1\"}");
            }
            return Response.status(404);
        });
    }

    private static String requeteDeRecherche(Request requete) {
        return URLDecoder.decode(requete.query(), StandardCharsets.UTF_8);
    }

    @Test
    void cs26_leDepotNAjouteAucunePermissionNiLienPublicEtRendLIdentifiantDeDrive() {
        driveVide();

        String identifiant = client().deposit(photo());

        assertThat(identifiant).isEqualTo("fichier-drive-1");
        assertThat(drive.requests()).noneSatisfy(requete -> assertThat(requete.path()).containsIgnoringCase("permission"));
        Request envoi = drive.requests().getLast();
        assertThat(envoi.is("POST", "/upload/drive/v3/files")).isTrue();
        assertThat(envoi.query()).isEqualTo("uploadType=multipart&fields=id");
        assertThat(envoi.header("Content-Type")).startsWith("multipart/related;boundary=");
        assertThat(envoi.bodyText()).doesNotContainIgnoringCase("permission").doesNotContain("anyone").doesNotContain("webViewLink");
    }

    @Test
    void r36_lEnvoiPorteLeNomGenereLeDossierLIdentifiantInvisibleEtLesOctetsDOrigine() {
        driveVide();

        client().deposit(photo());

        Request envoi = drive.requests().getLast();
        String corps = envoi.bodyText();
        assertThat(corps).contains("\"name\":\"2026-10-11_" + PHOTO + ".png\"")
                .contains("\"parents\":[\"dossier-drive-1\"]")
                .contains("\"appProperties\":{\"photoId\":\"" + PHOTO + "\"}")
                .contains("Content-Type: image/png");
        assertThat(corps).contains(new String(OCTETS, StandardCharsets.ISO_8859_1));
        assertThat(envoi.header("Authorization")).isEqualTo("Bearer jeton-d-essai");
    }

    @Test
    void cs26_leDossierDeLApplicationEstCreeAuPremierDepotSousLeNomConfigure() {
        driveVide();

        client().deposit(photo());

        Request creation = drive.requests().stream().filter(requete -> requete.is("POST", "/drive/v3/files")).findFirst().orElseThrow();
        assertThat(new String(creation.body(), StandardCharsets.UTF_8)).contains("\"mimeType\":\"application/vnd.google-apps.folder\"").contains("\"name\":\"Reçus du chalet\"");
    }

    @Test
    void r21_unFichierDejaDeposeSousLIdentifiantDeLaPhotoEstReutiliseSansCreation() {
        drive.answer(requete -> {
            if (requete.is("GET", "/drive/v3/files") && requeteDeRecherche(requete).contains("appProperties has")) {
                return Response.json("{\"files\":[{\"id\":\"fichier-existant\"}]}");
            }
            if (requete.is("GET", "/drive/v3/files")) {
                return Response.json("{\"files\":[{\"id\":\"dossier-existant\"}]}");
            }
            return Response.status(500);
        });

        assertThat(client().deposit(photo())).isEqualTo("fichier-existant");

        assertThat(drive.requests()).allSatisfy(requete -> assertThat(requete.method()).isEqualTo("GET"));
        assertThat(requeteDeRecherche(drive.requests().getLast())).contains("appProperties has { key='photoId' and value='" + PHOTO + "' }")
                .contains("'dossier-existant' in parents").contains("trashed=false");
    }

    @Test
    void r28_unDossierDejaCreeEstRetrouveParSonNomEtNonRecree() {
        drive.answer(requete -> {
            if (requete.is("GET", "/drive/v3/files") && requeteDeRecherche(requete).contains("appProperties has")) {
                return Response.json("{\"files\":[]}");
            }
            if (requete.is("GET", "/drive/v3/files")) {
                return Response.json("{\"files\":[{\"id\":\"dossier-existant\"}]}");
            }
            if (requete.is("POST", "/upload/drive/v3/files")) {
                return Response.json("{\"id\":\"fichier-drive-1\"}");
            }
            return Response.status(500);
        });

        client().deposit(photo());

        assertThat(drive.requests()).noneSatisfy(requete -> assertThat(requete.is("POST", "/drive/v3/files")).isTrue());
        assertThat(requeteDeRecherche(drive.requests().getFirst())).contains("name='Reçus du chalet'").contains("mimeType='application/vnd.google-apps.folder'");
    }

    @Test
    void a03_uneApostropheDuNomDeDossierEstEchappeeDansLaRecherche() {
        driveVide();
        JdkClientHttpRequestFactory fabrique = new JdkClientHttpRequestFactory();
        GoogleDriveClient client = new GoogleDriveClient(RestClient.builder().requestFactory(fabrique).baseUrl(drive.address()).build(), () -> jetons.get(), "l'app \\ test");

        client.deposit(photo());

        assertThat(requeteDeRecherche(drive.requests().getFirst())).contains("name='l\\'app \\\\ test'");
    }

    @Test
    void r20_sansAutorisationAucuneRequeteNePartEtLAutorisationManqueEtLaCopieAttend() {
        jetons = Optional::empty;

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.AUTORISATION_GOOGLE));

        assertThat(drive.requests()).isEmpty();
    }

    @Test
    void r20_unJetonIndisponibleSansRefusEstUnePannePassagere() {
        jetons = () -> {
            throw new OAuth2AuthorizationException(new OAuth2Error("temporarily_unavailable"));
        };

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOf(TransientGoogleException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503, 401})
    void r20_uneReponse429OuServeurOuUnJetonRefuseEstUnePannePassagere(int statut) {
        drive.answer(requete -> Response.status(statut));

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOf(TransientGoogleException.class);
    }

    @Test
    void ca53_unQuota429AvecRetryAfterDe120SecondesEstRapporteAuCycleDeCopie() {
        drive.answer(requete -> Response.status(429).withHeader("Retry-After", "120"));

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOfSatisfying(TransientGoogleException.class,
                e -> assertThat(e.retryAfter()).contains(java.time.Duration.ofSeconds(120)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-5", "99999999999999999999", "Wed, 21 Oct 2015 07:28:00 GMT"})
    void ca53_unRetryAfterAbsentIllisibleOuNegatifNEstPasRetenu(String valeur) {
        drive.answer(requete -> valeur.isEmpty() ? Response.status(503) : Response.status(503).withHeader("Retry-After", valeur));

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOfSatisfying(TransientGoogleException.class,
                e -> assertThat(e.retryAfter()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    void r20_unRefusOuUnDossierIntrouvableBloqueLaCopie(int statut) {
        drive.answer(requete -> Response.status(statut));

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOfSatisfying(BlockedGoogleException.class,
                e -> assertThat(e.motif()).isEqualTo(MotifBlocage.FICHIER_INACCESSIBLE));
    }

    @Test
    void r20_uneReponseDeSuccesIllisibleEstUnePannePassagere() {
        drive.answer(requete -> Response.text(200, "<html>mandataire</html>"));

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOf(TransientGoogleException.class);
    }

    @Test
    void r20_uneReponseDeCreationSansIdentifiantEstUnePannePassagere() {
        drive.answer(requete -> requete.is("GET", "/drive/v3/files") ? Response.json("{\"files\":[]}") : Response.json("{}"));

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOf(TransientGoogleException.class);
    }

    @Test
    void r20_uneConnexionRefuseeEstUnePannePassagere() {
        drive.stop();

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOf(TransientGoogleException.class);
    }

    @Test
    void r20_aucuneErreurNeRecopieLesOctetsDeLaPhotoNiLeCorpsDeLaReponse() {
        drive.answer(requete -> Response.text(500, "contenu-de-reponse-secret"));

        assertThatThrownBy(() -> client().deposit(photo())).isInstanceOf(TransientGoogleException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getMessage()) + e.getCause()).doesNotContain("contenu-de-reponse-secret"));
    }
}
