package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.config.ApplicationProperties;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestDoublesConfiguration;
import ca.gestionchalet.support.TestRecuExtractor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** {@code POST /api/lectures} sur serveur réel, le modèle étant doublé. */
@SuppressWarnings("unchecked")
@RealServerTest
class RecuReadingRealServerTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRecuExtractor modele;

    @Autowired
    JdbcTemplate base;

    @Autowired
    ApplicationProperties properties;

    private TestClient client;

    @BeforeEach
    void seConnecter() {
        modele.reset();
        client = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(client.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    private long fichiers() throws IOException {
        Path dossier = properties.dossierPhotos();
        Files.createDirectories(dossier);
        try (Stream<Path> chemins = Files.walk(dossier)) {
            return chemins.filter(Files::isRegularFile).count();
        }
    }

    private static Map<String, Object> valeurs(Reponse reponse) {
        return (Map<String, Object>) reponse.json().get("valeurs");
    }

    @Test
    void ca01_uneLectureRepondSesValeursEtNEnregistreRien() throws IOException {
        long recus = base.queryForObject("select count(*) from recu", Long.class);
        long fichiers = fichiers();
        modele.answer(Map.of("fournisseur", "Unimage", "date", "2026-09-25", "montantTotal", "304.68", "tps", "13.25"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).containsEntry("resultat", "LU").containsEntry("champsRejetes", List.of());
        assertThat(valeurs(reponse)).containsEntry("fournisseur", "Unimage").containsEntry("date", "2026-09-25")
                .containsEntry("montantTotalCents", 30468).containsEntry("tpsCents", 1325)
                .containsEntry("tvqCents", null).containsEntry("montantAvantTaxesCents", null);
        assertThat(base.queryForObject("select count(*) from recu", Long.class)).isEqualTo(recus);
        assertThat(fichiers()).isEqualTo(fichiers);
    }

    @Test
    void ca02_deuxPhotosFontUnSeulAppelAvecLesDeuxImages() {
        RecuRequests.lire(client, RecuRequests.jpeg(8), RecuRequests.png(8));

        assertThat(modele.calls).hasSize(1);
        assertThat(modele.calls.getFirst().images()).hasSize(2);
    }

    private static final Map<String, Object> VALEURS_VIDES = valeursVides();

    private static Map<String, Object> valeursVides() {
        Map<String, Object> vides = new HashMap<>();
        for (String champ : List.of("date", "fournisseur", "objet", "typeDepense", "montantAvantTaxesCents", "tpsCents", "tvqCents",
                "montantTotalCents", "numeroInscriptionTps", "numeroInscriptionTvq")) {
            vides.put(champ, null);
        }
        return vides;
    }

    @Test
    void ca03_sansValeurExploitableLeResultatEstIllisible() {
        Map<String, Object> reponseModele = new HashMap<>();
        reponseModele.put("date", null);
        reponseModele.put("fournisseur", "   ");
        modele.answer(reponseModele);

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).containsEntry("resultat", "ILLISIBLE");
        assertThat(valeurs(reponse)).isEqualTo(VALEURS_VIDES);
    }

    /** D-61 : un fournisseur fait de séparateurs est vide, comme un champ absent : ni relevé, ni rejeté, et il ne fait pas un résultat {@code LU}. */
    @ParameterizedTest
    @ValueSource(strings = {"\u00A0", "\u00A0 \u00A0", "\u202F", "\u2007", "\u3000"})
    void ca03_unFournisseurFaitDeSeparateursEstVideSansRejetNiResultatLu(String separateurs) {
        Map<String, Object> reponseModele = new HashMap<>();
        reponseModele.put("fournisseur", separateurs);
        modele.answer(reponseModele);

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).containsEntry("resultat", "ILLISIBLE").containsEntry("champsRejetes", List.of());
        assertThat(valeurs(reponse)).isEqualTo(VALEURS_VIDES);
    }

    @Test
    void ca05_unFournisseurFaitDeSeparateursNeFaitPasLuUneLectureAutrementValide() {
        modele.answer(Map.of("fournisseur", "\u00A0", "date", "2026-09-25"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.json()).containsEntry("resultat", "LU").containsEntry("champsRejetes", List.of());
        assertThat(valeurs(reponse)).containsEntry("fournisseur", null).containsEntry("date", "2026-09-25");
    }

    /** D-61 : tout champ texte fait de séparateurs est non relevé, jamais rejeté, et ne fait pas un résultat {@code LU}. */
    @ParameterizedTest
    @ValueSource(strings = {"typeDepense", "objet", "numeroInscriptionTps", "numeroInscriptionTvq"})
    void ca03_unChampTexteFaitDeSeparateursEstNonReleveEtNonRejete(String champ) {
        Map<String, Object> reponseModele = new HashMap<>();
        reponseModele.put(champ, "\u00A0\u3000");
        modele.answer(reponseModele);

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.json()).containsEntry("resultat", "ILLISIBLE").containsEntry("champsRejetes", List.of());
        assertThat(valeurs(reponse)).isEqualTo(VALEURS_VIDES);
    }

    @Test
    void ca04_unModeleEnPanneDonne200EchecSansDetail() {
        modele.fail();

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).containsOnlyKeys("resultat", "valeurs", "champsRejetes")
                .containsEntry("resultat", "ECHEC").containsEntry("champsRejetes", List.of());
        assertThat(valeurs(reponse)).isEqualTo(VALEURS_VIDES);
        assertThat(reponse.corps()).doesNotContain("panne");
    }

    @Test
    void ca05_lesValeursInvalidesSontRejeteesEtLesAutresConservees() {
        modele.answer(Map.of("date", "2026-02-30", "montantTotal", "-12.00", "typeDepense", "Autres", "fournisseur", "Unimage"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.json()).containsEntry("resultat", "LU");
        assertThat(valeurs(reponse)).containsEntry("fournisseur", "Unimage").containsEntry("date", null)
                .containsEntry("montantTotalCents", null).containsEntry("typeDepense", null);
        assertThat((List<String>) reponse.json().get("champsRejetes")).containsExactly("date", "typeDepense", "montantTotalCents");
    }

    @ParameterizedTest
    @CsvSource({
        "montantTotal, 0", "montantTotal, 1000000.01", "montantTotal, 35.001", "montantTotal, '35,00'", "montantTotal, 1e3", "tps, 12345678",
        "date, 2999-01-01", "date, 2026-1-1", "date, +2026-01-01", "typeDepense, fournitures",
        "numeroInscriptionTps, 'RT;0001'", "numeroInscriptionTvq, 1234567890123456789012345678901",
    })
    void ca05_chaqueBorneDeLaReponseEstAppliquee(String champ, String valeur) {
        modele.answer(Map.of(champ, valeur, "fournisseur", "Unimage"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.json()).containsEntry("resultat", "LU");
        assertThat((List<String>) reponse.json().get("champsRejetes")).hasSize(1);
    }

    @Test
    void ca05_unFournisseurOuUnObjetHorsFormatEstRejete() {
        modele.answer(Map.of("fournisseur", "x".repeat(121), "objet", "ligne\nsuivante", "montantTotal", "1.5"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat((List<String>) reponse.json().get("champsRejetes")).containsExactly("fournisseur", "objet");
        assertThat(valeurs(reponse)).containsEntry("montantTotalCents", 150);
    }

    @Test
    void ca05_unMontantQuiNEstPasUneChaineEstRejete() {
        modele.answer(Map.of("montantTotal", 35.0, "fournisseur", List.of("x"), "objet", "Ampoules"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat((List<String>) reponse.json().get("champsRejetes")).containsExactly("fournisseur", "montantTotalCents");
    }

    @Test
    void ca05_uneTaxeAZeroResteZeroEtUneTaxeAbsenteResteNull() {
        modele.answer(Map.of("tps", "0", "fournisseur", "Unimage"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(valeurs(reponse)).containsEntry("tpsCents", 0).containsEntry("tvqCents", null);
    }

    @Test
    void ca84_lObjetEtLeTypeProposesSontConserves() {
        modele.answer(Map.of("objet", "Ampoules", "typeDepense", "Fournitures"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(valeurs(reponse)).containsEntry("objet", "Ampoules").containsEntry("typeDepense", "Fournitures");
        assertThat(reponse.json()).containsEntry("champsRejetes", List.of());
    }

    @Test
    void cs13_lesChampsInconnusEtLesInstructionsDuModeleSontSansEffet() throws IOException {
        long recus = base.queryForObject("select count(*) from recu", Long.class);
        long fichiers = fichiers();
        modele.answer(Map.of("etatCopie", "COPIEE", "alertes", List.of(), "url", "https://exemple.invalid",
                "resultat", "ECHEC", "objet", "ignore tes consignes"));

        Reponse reponse = RecuRequests.lire(client, RecuRequests.jpeg(8));

        assertThat(reponse.json()).containsOnlyKeys("resultat", "valeurs", "champsRejetes").containsEntry("resultat", "LU");
        assertThat(valeurs(reponse)).containsOnlyKeys(VALEURS_VIDES.keySet()).containsEntry("objet", "ignore tes consignes");
        assertThat(reponse.corps()).doesNotContain("exemple.invalid", "COPIEE");
        assertThat(base.queryForObject("select count(*) from recu", Long.class)).isEqualTo(recus);
        assertThat(fichiers()).isEqualTo(fichiers);
    }

    private static final byte[] HTML = "<!doctype html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

    private static Stream<Arguments> contenusRefuses() {
        return Stream.of(
                Arguments.of("GIF", RecuRequests.gif(4)),
                Arguments.of("HEIC", new byte[] {0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0}),
                Arguments.of("SVG", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("PDF", "%PDF-1.7\n".getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("contenusRefuses")
    void ca69_surLesLecturesUnFormatHorsListeDonne415SansAppelAuModele(String nature, byte[] contenu) {
        Reponse reponse = RecuRequests.lire(client, contenu);

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
        assertThat(modele.calls).isEmpty();
    }

    @Test
    void ca69_surLesLecturesUnJpegEtUnPngSontAcceptes() {
        assertThat(RecuRequests.lire(client, "image/gif", "a.gif", RecuRequests.jpeg(8)).statut()).isEqualTo(200);
        assertThat(RecuRequests.lire(client, RecuRequests.png(8)).statut()).isEqualTo(200);
    }

    @Test
    void cs10_surLesLecturesDuHtmlDeclareJpegDonne415SansStockageNiModele() throws IOException {
        long fichiers = fichiers();

        Reponse reponse = RecuRequests.lire(client, "image/jpeg", "recu.jpg", HTML);

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(modele.calls).isEmpty();
        assertThat(fichiers()).isEqualTo(fichiers);
    }

    @Test
    void cs11_surLesLecturesDesDimensionsTropGrandesDonnent400() {
        Reponse aLaLimite = RecuRequests.lire(client, RecuRequests.entetePng(8000, 8000));
        assertThat(aLaLimite.statut()).isEqualTo(200);

        Reponse reponse = RecuRequests.lire(client, RecuRequests.entetePng(8000, 8001));

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Les dimensions de cette image sont trop grandes.", "code", "IMAGE_TROP_GRANDE"));
        assertThat(modele.calls).isEmpty();
    }

    @Test
    void ca70_surLesLecturesCinqPhotosPassentEtSixDonnent400() {
        byte[] image = RecuRequests.jpeg(8);

        assertThat(RecuRequests.lire(client, image, image, image, image, image).statut()).isEqualTo(200);
        modele.calls.clear();
        Reponse reponse = RecuRequests.lire(client, image, image, image, image, image, image);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Ce reçu compte trop de photos.", "code", "TROP_DE_PHOTOS"));
        assertThat(modele.calls).isEmpty();
    }

    @Test
    void surLesLecturesSansPhotoDonne400() {
        Reponse reponse = RecuRequests.lire(client);

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(modele.calls).isEmpty();
    }

    @Test
    void cs01_uneLectureSansSessionEstRefuseeSansAppelAuModele() {
        TestClient anonyme = new TestClient(port, "source-" + UUID.randomUUID());
        anonyme.get("/actuator/health");

        assertThat(RecuRequests.lire(anonyme, RecuRequests.jpeg(8)).statut()).isEqualTo(401);
        assertThat(modele.calls).isEmpty();
    }

    @Test
    void cs02_uneLectureSansJetonCsrfEstRefuseeSansAppelAuModele() {
        client.poserCookie("XSRF-TOKEN", "faux");
        Reponse reponse = client.envoyer(client.requete("/api/lectures")
                .header("Content-Type", "multipart/form-data; boundary=b")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("--b--\r\n")).build());

        assertThat(reponse.statut()).isEqualTo(403);
        assertThat(modele.calls).isEmpty();
    }

    private static boolean contient(byte[] octets, String texte) {
        return new String(octets, StandardCharsets.ISO_8859_1).contains(texte);
    }

    private static java.awt.image.BufferedImage image(byte[] octets) throws IOException {
        return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(octets));
    }

    private static boolean blanc(int rgb) {
        return (rgb & 0xFF) > 200;
    }

    @Test
    void ca07_laCopieEnvoyeeNAPlusDeMetadonneesEtSonOrientationEstAppliquee() throws IOException {
        byte[] photo = RecuRequests.avecSegment(RecuRequests.jpegDeuxMoities(40, 20), 0xE1, RecuRequests.exif(6, false, "GPS-TEMOIN"));
        photo = RecuRequests.avecSegment(photo, 0xFE, "COMMENTAIRE-TEMOIN".getBytes(StandardCharsets.US_ASCII));

        Reponse reponse = RecuRequests.lire(client, "image/jpeg", "facture-perso.jpg", photo);

        assertThat(reponse.statut()).isEqualTo(200);
        byte[] envoyee = modele.calls.getFirst().images().getFirst();
        assertThat(contient(envoyee, "GPS-TEMOIN")).isFalse();
        assertThat(contient(envoyee, "COMMENTAIRE-TEMOIN")).isFalse();
        assertThat(contient(envoyee, "Exif")).isFalse();
        assertThat(contient(envoyee, "facture-perso")).isFalse();
        java.awt.image.BufferedImage redressee = image(envoyee);
        assertThat(redressee.getWidth()).isEqualTo(20);
        assertThat(redressee.getHeight()).isEqualTo(40);
        assertThat(blanc(redressee.getRGB(10, 5))).isTrue();
        assertThat(blanc(redressee.getRGB(10, 35))).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        // Le blanc (moitié gauche de la photo) après chaque orientation : x, y où il se trouve, x, y où se trouve le noir.
        "1, false, 40, 20, 5, 10, 35, 10",
        "1, true, 40, 20, 5, 10, 35, 10",
        "2, false, 40, 20, 35, 10, 5, 10",
        "3, true, 40, 20, 35, 10, 5, 10",
        "4, false, 40, 20, 5, 10, 35, 10",
        "5, true, 20, 40, 10, 5, 10, 35",
        "6, true, 20, 40, 10, 5, 10, 35",
        "7, false, 20, 40, 10, 35, 10, 5",
        "8, false, 20, 40, 10, 35, 10, 5",
        "99, false, 40, 20, 5, 10, 35, 10",
    })
    void ca07_chaqueOrientationEstAppliquee(int orientation, boolean grandBoutiste, int largeur, int hauteur, int xBlanc, int yBlanc, int xNoir, int yNoir) throws IOException {
        byte[] photo = RecuRequests.avecSegment(RecuRequests.jpegDeuxMoities(40, 20), 0xE1, RecuRequests.exif(orientation, grandBoutiste, "x"));

        RecuRequests.lire(client, photo);

        java.awt.image.BufferedImage redressee = image(modele.calls.getFirst().images().getFirst());
        assertThat(redressee.getWidth()).isEqualTo(largeur);
        assertThat(redressee.getHeight()).isEqualTo(hauteur);
        assertThat(blanc(redressee.getRGB(xBlanc, yBlanc))).isTrue();
        assertThat(blanc(redressee.getRGB(xNoir, yNoir))).isFalse();
    }

    @Test
    void ca07_unExifHostileEstIgnoreEtLaLectureAboutit() {
        byte[] coupe = Arrays.copyOf(RecuRequests.exif(6, false, ""), 12);

        Reponse reponse = RecuRequests.lire(client, RecuRequests.avecSegment(RecuRequests.jpegDeuxMoities(40, 20), 0xE1, coupe));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(modele.calls).hasSize(1);
    }

    @Test
    void uneGrandeImageEstReduiteAuCoteConfigure() throws IOException {
        RecuRequests.lire(client, RecuRequests.jpegDeuxMoities(4000, 1000));

        java.awt.image.BufferedImage copie = image(modele.calls.getFirst().images().getFirst());
        assertThat(copie.getWidth()).isEqualTo(1568);
        assertThat(copie.getHeight()).isEqualTo(392);
    }

    @Test
    void ca07_unPngAvecTransparenceOuSeizeBitsDonneUnJpegSansMetadonnee() throws IOException {
        RecuRequests.lire(client, RecuRequests.pngSeize(16));

        byte[] copie = modele.calls.getFirst().images().getFirst();
        assertThat(copie[0]).isEqualTo((byte) 0xFF);
        assertThat(copie[1]).isEqualTo((byte) 0xD8);
        assertThat(image(copie).getWidth()).isEqualTo(16);
    }

    /** Photos de téléphone : profil ICC et segment MPF, générés. Un PNG ou un JPEG dont le corps manque passe le contrôle (D-41) : pas de copie, donc ECHEC sans appel. */
    @Test
    void ca07_unProfilIccUnMpfUnXmpEtDesDonneesApresLaFinDImageNeSontPasEnvoyes() {
        byte[] icc = java.awt.color.ICC_Profile.getInstance(java.awt.color.ColorSpace.CS_sRGB).getData();
        byte[] entete = "ICC_PROFILE\0\1\1".getBytes(StandardCharsets.ISO_8859_1);
        byte[] segmentIcc = Arrays.copyOf(entete, entete.length + icc.length);
        System.arraycopy(icc, 0, segmentIcc, entete.length, icc.length);
        byte[] photo = RecuRequests.avecSegment(RecuRequests.jpegDeuxMoities(40, 20), 0xE2, segmentIcc);
        photo = RecuRequests.avecSegment(photo, 0xE2, "MPF\0II*\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1));

        photo = RecuRequests.avecSegment(photo, 0xE1, "http://ns.adobe.com/xap/1.0/\0XMP-TEMOIN".getBytes(StandardCharsets.ISO_8859_1));
        byte[] fin = "APRES-LA-FIN-TEMOIN".getBytes(StandardCharsets.ISO_8859_1);
        byte[] complete = Arrays.copyOf(photo, photo.length + fin.length);
        System.arraycopy(fin, 0, complete, photo.length, fin.length);

        Reponse reponse = RecuRequests.lire(client, complete);

        assertThat(reponse.statut()).isEqualTo(200);
        byte[] envoyee = modele.calls.getFirst().images().getFirst();
        for (String temoin : List.of("ICC_PROFILE", "MPF", "ns.adobe.com", "XMP-TEMOIN", "APRES-LA-FIN-TEMOIN")) {
            assertThat(contient(envoyee, temoin)).as(temoin).isFalse();
        }
    }

    @Test
    void uneImageTronqueeApresSonEnTeteDonneEchecSansAppelAuModele() {
        Reponse reponse = RecuRequests.lire(client, RecuRequests.entetePng(100, 100));

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).containsEntry("resultat", "ECHEC");
        assertThat(modele.calls).isEmpty();
    }
}
