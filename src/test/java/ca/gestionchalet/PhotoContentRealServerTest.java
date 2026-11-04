package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.config.ApplicationProperties;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Le format et les limites d'une photo se lisent dans ses octets, jamais dans ce que déclare le client (PRD, section 11.4). */
@RealServerTest
class PhotoContentRealServerTest {

    private static final Map<String, Object> FORMAT_REFUSE = Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE");

    @LocalServerPort
    int port;

    @Autowired
    ApplicationProperties properties;

    @Autowired
    JdbcTemplate base;

    private TestClient client;

    @BeforeEach
    void seConnecter() {
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private record Etat(long recus, long photos, List<Path> fichiers) {
    }

    private Etat etat() throws IOException {
        Files.createDirectories(properties.dossierPhotos());
        try (Stream<Path> chemins = Files.walk(properties.dossierPhotos())) {
            return new Etat(lignes("recu"), lignes("photo"), chemins.filter(Files::isRegularFile).sorted().toList());
        }
    }

    private long lignes(String table) {
        return base.queryForObject("select count(*) from " + table, Long.class);
    }

    private static byte[] texte(String contenu) {
        return contenu.getBytes(StandardCharsets.UTF_8);
    }

    private static Stream<Arguments> contenusRefuses() {
        return Stream.of(
                Arguments.of("GIF", RecuRequests.gif(4)),
                Arguments.of("HEIC", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0}),
                Arguments.of("SVG", texte("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>")),
                Arguments.of("PDF", texte("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n")),
                Arguments.of("HTML", texte("<!doctype html><html><script>alert(1)</script></html>")),
                Arguments.of("script", texte("#!/bin/sh\necho essai\n")),
                Arguments.of("un seul octet", new byte[] {(byte) 0xFF}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contenusRefuses")
    void ca69_cs10_unContenuHorsListeDeclareJpegEstRefuseSansRienEnregistrer(String nature, byte[] contenu) throws IOException {
        Etat avant = etat();

        Reponse reponse = RecuRequests.validerDeclare(client, RecuRequests.corps(RecuRequests.valeurs()), "image/jpeg", "recu.jpg", contenu);

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(FORMAT_REFUSE);
        assertThat(etat()).isEqualTo(avant);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> premierePhoto(Reponse reponse) {
        return ((List<Map<String, Object>>) reponse.json().get("photos")).getFirst();
    }

    private static Stream<Arguments> imagesAcceptees() {
        return Stream.of(
                Arguments.of(RecuRequests.jpeg(8), "JPEG", ".jpg"),
                Arguments.of(RecuRequests.png(8), "PNG", ".png"));
    }

    /** Le type déclaré ment dans les deux sens : ni l'en-tête ni le nom ne décident du format conservé, et les octets restent ceux de l'envoi (CA71). */
    @ParameterizedTest
    @MethodSource("imagesAcceptees")
    void ca69_ca71_leFormatConserveEstCeluiDuContenuEtLesOctetsRestentIntacts(byte[] image, String format, String extension) throws IOException {
        Etat avant = etat();

        Reponse reponse = RecuRequests.validerDeclare(client, RecuRequests.corps(RecuRequests.valeurs()), "text/html", "recu.html", image);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(premierePhoto(reponse)).containsEntry("format", format).containsEntry("taille", image.length);
        List<Path> nouveaux = new ArrayList<>(etat().fichiers());
        nouveaux.removeAll(avant.fichiers());
        assertThat(nouveaux).singleElement().satisfies(fichier -> {
            assertThat(fichier.getFileName().toString()).endsWith(extension);
            assertThat(Files.readAllBytes(fichier)).isEqualTo(image);
        });
    }

    private static byte[] coller(byte[]... morceaux) {
        ByteArrayOutputStream octets = new ByteArrayOutputStream();
        for (byte[] morceau : morceaux) {
            octets.writeBytes(morceau);
        }
        return octets.toByteArray();
    }

    private static Stream<Arguments> entetesIllisibles() {
        byte[] signaturePng = Arrays.copyOf(RecuRequests.png(4), 8);
        byte[] signatureJpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        byte[] html = texte("<!doctype html><html><script>alert(1)</script></html>");
        return Stream.of(
                Arguments.of("signature PNG seule", signaturePng),
                Arguments.of("signature PNG puis HTML", coller(signaturePng, html)),
                Arguments.of("PNG tronqué dans son en-tête", Arrays.copyOf(RecuRequests.png(4), 20)),
                Arguments.of("PNG de largeur nulle", RecuRequests.entetePng(0, 10)),
                Arguments.of("signature JPEG seule", signatureJpeg),
                Arguments.of("signature JPEG puis HTML", coller(signatureJpeg, html)),
                Arguments.of("JPEG tronqué dans son en-tête", Arrays.copyOf(RecuRequests.jpeg(4), 12)),
                Arguments.of("JPEG de hauteur nulle", RecuRequests.jpegAnnoncant(10, 0)));
    }

    /** La signature dit le format ; c'est le lecteur d'en-tête qui dit si le fichier est bien de ce format. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("entetesIllisibles")
    void ca69_uneSignatureAccepteeSansEnTeteLisibleDonneImageIllisible(String nature, byte[] contenu) throws IOException {
        Etat avant = etat();

        Reponse reponse = RecuRequests.validerDeclare(client, RecuRequests.corps(RecuRequests.valeurs()), "image/jpeg", "recu.jpg", contenu);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Cette image est illisible.", "code", "IMAGE_ILLISIBLE"));
        assertThat(etat()).isEqualTo(avant);
    }

    private static Stream<Arguments> imagesTropGrandes() {
        return Stream.of(
                Arguments.of("PNG de 64 Mpx + une ligne", RecuRequests.entetePng(8000, 8001)),
                Arguments.of("PNG de 4 Gpx", RecuRequests.entetePng(65536, 65536)),
                Arguments.of("PNG aux dimensions maximales", RecuRequests.entetePng(Integer.MAX_VALUE, Integer.MAX_VALUE)),
                Arguments.of("JPEG de 400 Mpx", RecuRequests.jpegAnnoncant(20000, 20000)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("imagesTropGrandes")
    void cs11_uneImageLegereAuxDimensionsExcessivesEstRefuseeSansRienEnregistrer(String nature, byte[] contenu) throws IOException {
        Etat avant = etat();

        Reponse reponse = RecuRequests.validerDeclare(client, RecuRequests.corps(RecuRequests.valeurs()), "image/png", "recu.png", contenu);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Les dimensions de cette image sont trop grandes.", "code", "IMAGE_TROP_GRANDE"));
        assertThat(etat()).isEqualTo(avant);
    }

    @Test
    void cs11_uneImageAuxDimensionsMaximalesEstAcceptee() {
        Reponse reponse = RecuRequests.validerDeclare(client, RecuRequests.corps(RecuRequests.valeurs()), "image/png", "recu.png", RecuRequests.entetePng(8000, 8000));

        assertThat(reponse.statut()).isEqualTo(201);
    }

    /** Une image valide suivie d'un autre contenu reste cette image : rien ne l'exécutera, elle n'est servie que sous le type de son format. */
    @Test
    void cs10_unPngValideSuiviDeHtmlEstConserveCommePngSansReecriture() throws IOException {
        Etat avant = etat();
        byte[] contenu = coller(RecuRequests.png(4), texte("<html><script>alert(1)</script></html>"));

        Reponse reponse = RecuRequests.validerDeclare(client, RecuRequests.corps(RecuRequests.valeurs()), "text/html", "recu.html", contenu);

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(premierePhoto(reponse)).containsEntry("format", "PNG");
        List<Path> nouveaux = new ArrayList<>(etat().fichiers());
        nouveaux.removeAll(avant.fichiers());
        assertThat(Files.readAllBytes(nouveaux.getFirst())).isEqualTo(contenu);
    }
}
