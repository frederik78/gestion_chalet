package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ChromeSession;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestDoublesConfiguration;
import ca.gestionchalet.support.TestRecuExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Écrans dans un vrai navigateur, serveur et base réels. Ces tests sont IGNORÉS dans un {@code verify} sans {@code essai.chrome} ;
 * dès que la propriété existe, même vide, un binaire absent ou inutilisable les fait échouer. La CI la fournit et contrôle qu'ils ont tourné.
 * Pour les lancer :
 * {@code scripts/verify-clean.sh -- "-Dessai.chrome=/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"},
 * avec {@code -Dessai.captures=<dossier>} pour garder les captures.
 */
@RealServerTest
@TestPropertySource(properties = {"recus.securite.cookie-securise=false", "recus.copie.annees.2026.fichier=", "recus.copie.annees.2026.onglet=", "recus.copie.annees.2025.fichier=", "recus.copie.annees.2025.onglet="})
@EnabledIfSystemProperty(named = "essai.chrome", matches = ".*")
class RecuScreensChromeTest {

    /** Retient ce que la page envoie vraiment à POST /api/recus, puis laisse passer la requête. */
    private static final String ENREGISTRER_LES_ENVOIS = """
            window.envois = [];
            window.fetchOrigine = window.fetch;
            window.fetch = async (chemin, options) => {
              if (String(chemin) === '/api/recus') {
                const recu = JSON.parse(await options.body.get('recu').text());
                window.envois.push({ cle: recu.cleIdempotence, photos: options.body.getAll('photos').length, recu });
              }
              return window.fetchOrigine(chemin, options);
            };
            true""";

    /** La première réponse de POST /api/recus se perd : le serveur a bien traité la requête, la page voit une panne. */
    private static final String PERDRE_LA_PREMIERE_REPONSE = """
            window.fetchAvantPerte = window.fetch;
            window.reponsesPerdues = 0;
            window.fetch = async (chemin, options) => {
              const reponse = await window.fetchAvantPerte(chemin, options);
              if (String(chemin) === '/api/recus' && window.reponsesPerdues === 0) {
                window.reponsesPerdues = 1;
                throw new TypeError('Failed to fetch');
              }
              return reponse;
            };
            true""";

    private static final String NOMBRE_DE_PHOTOS_AU_DEPART = "document.querySelectorAll('#photos-choisies canvas').length";
    private static final String NOMBRE_DE_PHOTOS_A_VALIDER = "document.querySelectorAll('#photos-a-valider canvas').length";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    TestRecuExtractor modele;

    @TempDir
    Path dossier;

    private ChromeSession chrome;
    private Path image1;
    private Path image2;

    @BeforeEach
    void ouvrirEtSeConnecter() throws IOException, InterruptedException {
        modele.reset();
        image1 = Files.write(dossier.resolve("image-1.png"), RecuRequests.png(300));
        image2 = Files.write(dossier.resolve("image-2.png"), RecuRequests.png(200));
        chrome = new ChromeSession(System.getProperty("essai.chrome"), Files.createDirectory(dossier.resolve("profil")));
        chrome.open("http://localhost:" + port + "/");
        chrome.waitUntil("document.readyState === 'complete' && !!document.getElementById('mot-de-passe')");
        chrome.eval("document.getElementById('mot-de-passe').value = '" + TestDoublesConfiguration.MOT_DE_PASSE + "';"
                + "document.getElementById('connexion').requestSubmit(); true");
        chrome.waitUntil("location.pathname === '/accueil.html' && document.readyState === 'complete' && document.querySelectorAll('#typeDepense option').length > 1");
    }

    @AfterEach
    void fermer() throws InterruptedException {
        chrome.close();
    }

    private void capturer(String nom) throws IOException {
        String captures = System.getProperty("essai.captures");
        if (captures != null) {
            chrome.screenshot(Path.of(captures).resolve(nom));
        }
    }

    private void cliquer(String id) {
        chrome.eval("document.getElementById('" + id + "').click(); true");
    }

    private void saisir(String id, String valeur) {
        chrome.eval("document.getElementById('" + id + "').value = '" + valeur + "'; true");
    }

    private String valeur(String id) {
        return chrome.eval("document.getElementById('" + id + "').value").asString();
    }

    private boolean visible(String id) {
        return chrome.eval("document.getElementById('" + id + "').offsetParent !== null").asBoolean();
    }

    private int nombre(String expression) {
        return chrome.eval(expression).asInt();
    }

    private long recus() {
        return base.queryForObject("select count(*) from recu", Long.class);
    }

    private void allerALaValidationAvecDeuxPhotos() throws InterruptedException {
        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        chrome.chooseFiles("#importer", image2);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 2");
        cliquer("continuer");
        chrome.waitUntil(NOMBRE_DE_PHOTOS_A_VALIDER + " === 2");
    }

    private void saisirLesValeurs(String fournisseur) {
        saisir("date", "2026-10-11");
        saisir("fournisseur", fournisseur);
        saisir("typeDepense", "Fournitures");
        saisir("montantTotal", "304,68");
        saisir("tps", "13,25");
    }

    @Test
    void ca76_lEcranProposePhotographierEtImporterEtUneSecondePhotoSAjouteEtSeRetire() throws InterruptedException, IOException {
        assertThat(visible("photographier")).isTrue();
        assertThat(visible("importer")).isTrue();
        assertThat(chrome.eval("document.getElementById('photographier').getAttribute('capture')").asString()).isEqualTo("environment");
        assertThat(chrome.eval("document.getElementById('importer').hasAttribute('capture')").asBoolean()).isFalse();
        assertThat(visible("continuer")).isTrue();
        assertThat(chrome.eval("document.getElementById('continuer').disabled").asBoolean()).isTrue();

        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        chrome.chooseFiles("#importer", image2);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 2");
        assertThat(chrome.eval("document.getElementById('continuer').disabled").asBoolean()).isFalse();
        capturer("prise-de-vue.png");

        chrome.eval("document.querySelectorAll('#photos-choisies button')[1].click(); true");
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        assertThat(nombre("document.querySelector('#photos-choisies canvas').width")).isPositive();
    }

    @Test
    void ca79_abandonnerNEnvoieRienEtLEcranDeDepartNeMontreAucunePhoto() throws InterruptedException {
        long avant = recus();
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur abandonné");

        cliquer("abandonner");

        chrome.waitUntil("document.getElementById('prise-de-vue').offsetParent !== null");
        assertThat(visible("validation")).isFalse();
        assertThat(nombre(NOMBRE_DE_PHOTOS_AU_DEPART)).isZero();
        assertThat(nombre(NOMBRE_DE_PHOTOS_A_VALIDER)).isZero();
        assertThat(nombre("window.envois.length")).isZero();
        assertThat(recus()).isEqualTo(avant);

        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        cliquer("continuer");
        chrome.waitUntil(NOMBRE_DE_PHOTOS_A_VALIDER + " === 1");
        assertThat(valeur("fournisseur")).isEmpty();
    }

    /** La réponse de POST /api/recus attend {@code window.libererLEnvoi()}, puis l'envoi échoue. */
    private static final String RETENIR_L_ENVOI_PUIS_ECHOUER = """
            window.fetchAvantRetenue = window.fetch;
            window.fetch = async (chemin, options) => {
              if (String(chemin) !== '/api/recus') {
                return window.fetchAvantRetenue(chemin, options);
              }
              window.fetch = window.fetchAvantRetenue;
              await new Promise((liberer) => { window.libererLEnvoi = liberer; });
              throw new TypeError('Failed to fetch');
            };
            true""";

    @Test
    void ca79_abandonnerEstInactifPendantLEnvoiEtRevientApresUneErreurOuUnSucces() throws InterruptedException {
        long avant = recus();
        chrome.eval(RETENIR_L_ENVOI_PUIS_ECHOUER);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur en cours");
        cliquer("valider");
        chrome.waitUntil("!!window.libererLEnvoi");

        assertThat(chrome.eval("document.getElementById('abandonner').disabled").asBoolean()).isTrue();
        cliquer("abandonner");
        assertThat(visible("validation")).isTrue();
        assertThat(nombre(NOMBRE_DE_PHOTOS_A_VALIDER)).isEqualTo(2);

        chrome.eval("window.libererLEnvoi(); true");
        chrome.waitUntil("document.getElementById('message-validation').textContent !== ''");
        assertThat(chrome.eval("document.getElementById('abandonner').disabled").asBoolean()).isFalse();
        assertThat(recus()).isEqualTo(avant);

        cliquer("valider");
        chrome.waitUntil("document.getElementById('prise-de-vue').offsetParent !== null");
        assertThat(chrome.eval("document.getElementById('abandonner').disabled").asBoolean()).isFalse();
        assertThat(recus()).isEqualTo(avant + 1);
    }

    @Test
    void ca81_apresUnePanneReseauLeRenvoiGardeValeursPhotosEtCle() throws InterruptedException, IOException {
        long avant = recus();
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur panne");
        capturer("validation.png");

        chrome.setOffline(true);
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1 && document.getElementById('message-validation').textContent !== ''");
        capturer("validation-panne.png");

        assertThat(recus()).isEqualTo(avant);
        assertThat(visible("validation")).isTrue();
        assertThat(valeur("fournisseur")).isEqualTo("Fournisseur panne");
        assertThat(valeur("montantTotal")).isEqualTo("304,68");
        assertThat(nombre(NOMBRE_DE_PHOTOS_A_VALIDER)).isEqualTo(2);

        chrome.setOffline(false);
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 2 && document.getElementById('prise-de-vue').offsetParent !== null");

        JsonNode envois = chrome.eval("window.envois");
        assertThat(envois.get(1).get("cle").asString()).isEqualTo(envois.get(0).get("cle").asString());
        assertThat(envois.get(1).get("photos").asInt()).isEqualTo(2);
        assertThat(envois.get(1).get("recu")).isEqualTo(envois.get(0).get("recu"));
        JsonNode valeurs = envois.get(1).get("recu").get("valeurs");
        assertThat(valeurs.get("montantTotalCents").asInt()).isEqualTo(30468);
        assertThat(valeurs.get("tpsCents").asInt()).isEqualTo(1325);
        assertThat(valeurs.get("tvqCents").isNull()).isTrue();
        assertThat(recus()).isEqualTo(avant + 1);
        assertThat(nombre(NOMBRE_DE_PHOTOS_AU_DEPART)).isZero();
    }

    @Test
    void ca81_uneReponsePerdueNeCreeQuUnSeulRecuAuRenvoi() throws InterruptedException {
        long avant = recus();
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        chrome.eval(PERDRE_LA_PREMIERE_REPONSE);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur réponse perdue");

        cliquer("valider");
        chrome.waitUntil("document.getElementById('message-validation').textContent !== ''");
        assertThat(recus()).isEqualTo(avant + 1);
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 2 && document.getElementById('prise-de-vue').offsetParent !== null");

        JsonNode envois = chrome.eval("window.envois");
        assertThat(envois.get(1).get("cle").asString()).isEqualTo(envois.get(0).get("cle").asString());
        assertThat(recus()).isEqualTo(avant + 1);
    }

    @Test
    void ca18_uneCorrectionApresUneReponsePerdueAvertitQuElleNEstPasAppliquee() throws InterruptedException, IOException {
        long avant = recus();
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        chrome.eval(PERDRE_LA_PREMIERE_REPONSE);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur corrigé");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1 && document.getElementById('message-validation').textContent !== ''");

        saisir("montantTotal", "300,00");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 2 && document.getElementById('message-validation').textContent.includes('pas été appliquée')");
        capturer("validation-conflit.png");

        assertThat(chrome.eval("document.getElementById('message-validation').textContent").asString())
                .isEqualTo("Ce reçu était déjà enregistré : votre modification n'a pas été appliquée. Abandonnez pour revenir au départ.");
        assertThat(chrome.eval("document.getElementById('message-succes').textContent").asString()).isEmpty();
        assertThat(visible("validation")).isTrue();
        assertThat(valeur("montantTotal")).isEqualTo("300,00");
        assertThat(recus()).isEqualTo(avant + 1);
        assertThat(base.queryForObject("select count(*) from recu where fournisseur = 'Fournisseur corrigé' and montant_total_cents = 30468", Long.class)).isEqualTo(1);

        cliquer("abandonner");
        chrome.waitUntil("document.getElementById('prise-de-vue').offsetParent !== null");
        assertThat(nombre(NOMBRE_DE_PHOTOS_AU_DEPART)).isZero();
    }

    /** Le serveur dit une autre raison de blocage que le Sheet non configuré : la page revient à son message simple. */
    private static final String AUTRE_MOTIF_DE_BLOCAGE = """
            window.fetchAvantMotif = window.fetch;
            window.fetch = async (chemin, options) => {
              const reponse = await window.fetchAvantMotif(chemin, options);
              if (String(chemin) !== '/api/recus') {
                return reponse;
              }
              const recu = await reponse.json();
              recu.motifBlocage = 'AUTORISATION_GOOGLE';
              return new Response(JSON.stringify(recu), { status: reponse.status, headers: { 'Content-Type': 'application/json' } });
            };
            true""";

    @Test
    void d62_unSheetNonConfigureLEcranDitQueLaCopieAttend() throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur sans Sheet");
        cliquer("valider");
        chrome.waitUntil("document.getElementById('message-succes').textContent !== ''");

        assertThat(chrome.eval("document.getElementById('message-succes').textContent").asString())
                .isEqualTo("Reçu enregistré. Copie vers Google en attente : fichier ou onglet du Sheet non configuré.");
        assertThat(base.queryForObject("select motif_blocage from recu where fournisseur = 'Fournisseur sans Sheet'", String.class)).isEqualTo("FICHIER_INACCESSIBLE");
    }

    @Test
    void d62_unAutreMotifDeBlocageGardeLeMessageSimple() throws InterruptedException {
        chrome.eval(AUTRE_MOTIF_DE_BLOCAGE);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur autre motif");
        cliquer("valider");
        chrome.waitUntil("document.getElementById('message-succes').textContent !== ''");

        assertThat(chrome.eval("document.getElementById('message-succes').textContent").asString()).isEqualTo("Reçu enregistré.");
    }

    /** En HTTP sur une adresse du réseau local (mode dev, téléphone), le contexte n'est pas sécurisé : {@code crypto.randomUUID} n'existe pas. */
    @Test
    void cs29_modeDev_sansRandomUUIDLeBoutonContinuerFonctionneEtLaCleEstUnUuidV4() throws InterruptedException {
        chrome.eval("delete Crypto.prototype.randomUUID; typeof crypto.randomUUID === 'undefined'");
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Sans randomUUID");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1");

        JsonNode envois = chrome.eval("window.envois");
        assertThat(envois.get(0).get("cle").asString()).matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }

    @Test
    void ca81_uneNouvelleValidationApresUnSuccesPrendUneAutreCle() throws InterruptedException {
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Premier");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1 && document.getElementById('prise-de-vue').offsetParent !== null");
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Second");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 2 && document.getElementById('prise-de-vue').offsetParent !== null");

        JsonNode envois = chrome.eval("window.envois");
        assertThat(envois.get(1).get("cle").asString()).isNotEqualTo(envois.get(0).get("cle").asString());
    }

    /** L'écran laisse passer ce fichier, d'après son extension : la lecture lit son contenu, le refuse, et la photo se retire là où elle est. */
    @Test
    void ca69_cs10_unFichierDeguiseEstRefuseDesLaLectureEtSeRetire() throws InterruptedException, IOException {
        Path deguise = Files.writeString(dossier.resolve("deguise.png"), "<!doctype html><html><script>alert(1)</script></html>");
        chrome.chooseFiles("#importer", deguise);
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 1");
        cliquer("continuer");
        chrome.waitUntil("document.getElementById('message-depart').textContent !== ''");
        capturer("lecture-format-refuse.png");

        assertThat(chrome.eval("document.getElementById('message-depart').textContent").asString()).isEqualTo("Une photo n'est pas une image JPEG ou PNG.");
        assertThat(visible("validation")).isFalse();
        assertThat(modele.calls).isEmpty();

        chrome.eval("document.querySelector('#photos-choisies button').click(); true");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 0");
        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        cliquer("continuer");
        chrome.waitUntil(NOMBRE_DE_PHOTOS_A_VALIDER + " === 1");
        assertThat(modele.calls).hasSize(1);
    }

    /** D-41 : la lecture est court-circuitée pour atteindre le refus de la validation, puis le retour retire la photo sans toucher à la saisie. */
    @Test
    void ca69_cs10_uneValidationRefuseePermetDeRetirerLaPhotoSansPerdreLaSaisie() throws InterruptedException, IOException {
        long avant = recus();
        Path deguise = Files.writeString(dossier.resolve("deguise.png"), "<!doctype html><html><script>alert(1)</script></html>");
        chrome.eval("""
                window.fetchOrigine = window.fetch;
                window.fetch = async (chemin, options) => String(chemin) === '/api/lectures'
                  ? new Response(JSON.stringify({ resultat: 'ECHEC', valeurs: {}, champsRejetes: [] }), { status: 200 })
                  : window.fetchOrigine(chemin, options);
                true""");
        chrome.chooseFiles("#importer", deguise);
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 1");
        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 2");
        cliquer("continuer");
        chrome.waitUntil("document.querySelectorAll('#photos-a-valider figure').length === 2");
        saisirLesValeurs("Fournisseur déguisé");

        cliquer("valider");
        chrome.waitUntil("document.getElementById('message-validation').textContent !== ''");
        capturer("validation-format-refuse.png");
        assertThat(chrome.eval("document.getElementById('message-validation').textContent").asString())
                .isEqualTo("Une photo n'est pas une image JPEG ou PNG. Rien n'a été enregistré.");
        assertThat(recus()).isEqualTo(avant);

        cliquer("retour-photos");
        chrome.waitUntil("document.getElementById('prise-de-vue').offsetParent !== null");
        chrome.eval("document.querySelector('#photos-choisies button').click(); true");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 1");
        cliquer("continuer");
        chrome.waitUntil("document.getElementById('validation').offsetParent !== null");
        chrome.waitUntil(NOMBRE_DE_PHOTOS_A_VALIDER + " === 1");
        assertThat(valeur("fournisseur")).isEqualTo("Fournisseur déguisé");
        assertThat(valeur("montantTotal")).isEqualTo("304,68");

        cliquer("valider");
        chrome.waitUntil("document.getElementById('prise-de-vue').offsetParent !== null && document.getElementById('message-succes').textContent !== ''");
        assertThat(recus()).isEqualTo(avant + 1);
    }

    @Test
    void ca77_lEcranAfficheLesPhotosALaCoteDuFormulairePrerempliUneTaxeAbsenteResteVide() throws InterruptedException, IOException {
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        Map<String, Object> champs = new HashMap<>(Map.of("date", "2026-09-25", "fournisseur", "<img src=x onerror=alert(1)>", "objet", "Ampoules", "typeDepense", "Fournitures",
                "montantAvantTaxes", "265.00", "tvq", "0", "montantTotal", "304.68", "numeroInscriptionTps", "123456789RT0001"));
        champs.put("tps", null);
        modele.answer(champs);

        allerALaValidationAvecDeuxPhotos();
        capturer("validation-preremplie.png");

        assertThat(modele.calls).hasSize(1);
        assertThat(modele.calls.getFirst().images()).hasSize(2);
        assertThat(visible("photos-a-valider")).isTrue();
        assertThat(nombre("document.querySelector('.a-cote').contains(document.getElementById('photos-a-valider')) && document.querySelector('.a-cote').contains(document.getElementById('formulaire-recu')) ? 1 : 0")).isEqualTo(1);
        assertThat(valeur("date")).isEqualTo("2026-09-25");
        assertThat(valeur("fournisseur")).isEqualTo("<img src=x onerror=alert(1)>");
        assertThat(valeur("objet")).isEqualTo("Ampoules");
        assertThat(valeur("typeDepense")).isEqualTo("Fournitures");
        assertThat(valeur("montantAvantTaxes")).isEqualTo("265,00");
        assertThat(valeur("tps")).isEmpty();
        assertThat(valeur("tvq")).isEqualTo("0,00");
        assertThat(valeur("montantTotal")).isEqualTo("304,68");
        assertThat(valeur("numeroTps")).isEqualTo("123456789RT0001");
        assertThat(valeur("numeroTvq")).isEmpty();
        assertThat(visible("relancer-lecture")).isFalse();
        assertThat(nombre("document.querySelectorAll('img').length")).isZero();

        saisir("fournisseur", "Unimage");
        saisir("montantTotal", "300,00");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1 && document.getElementById('prise-de-vue').offsetParent !== null");
        JsonNode recu = chrome.eval("window.envois").get(0).get("recu");
        assertThat(recu.get("montantTotalLuCents").asInt()).isEqualTo(30468);
        assertThat(recu.get("valeurs").get("montantTotalCents").asInt()).isEqualTo(30000);
        assertThat(recu.get("valeurs").get("tpsCents").isNull()).isTrue();
        assertThat(recu.get("valeurs").get("tvqCents").asInt()).isZero();
    }

    @Test
    void ca77_unChampRejeteEstSignaleAVerifier() throws InterruptedException {
        modele.answer(Map.of("fournisseur", "Unimage", "montantTotal", "-1"));

        allerALaValidationAvecDeuxPhotos();

        assertThat(chrome.eval("document.getElementById('message-lecture').textContent").asString()).isEqualTo("À vérifier, valeur non retenue : le montant total.");
        assertThat(valeur("montantTotal")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "true | La lecture a échoué. Saisissez les valeurs ou relancez la lecture.",
        "false | Ce reçu n'a pas pu être lu. Saisissez les valeurs ou relancez la lecture.",
    })
    void ca78_uneLectureSansValeurMontreUnMessageUnFormulaireVideLaPhotoEtRelancer(boolean panne, String message) throws InterruptedException, IOException {
        if (panne) {
            modele.fail();
        }

        allerALaValidationAvecDeuxPhotos();
        capturer(panne ? "lecture-echec.png" : "lecture-illisible.png");

        assertThat(chrome.eval("document.getElementById('message-lecture').textContent").asString()).isEqualTo(message);
        assertThat(valeur("fournisseur")).isEmpty();
        assertThat(valeur("montantTotal")).isEmpty();
        assertThat(nombre(NOMBRE_DE_PHOTOS_A_VALIDER)).isEqualTo(2);
        assertThat(visible("relancer-lecture")).isTrue();

        saisir("objet", "Saisi à la main");
        modele.answer(Map.of("objet", "Proposé", "fournisseur", "Unimage"));
        cliquer("relancer-lecture");
        chrome.waitUntil("document.getElementById('fournisseur').value === 'Unimage'");

        assertThat(modele.calls).hasSize(2);
        assertThat(valeur("objet")).isEqualTo("Saisi à la main");
        assertThat(chrome.eval("document.getElementById('message-lecture').textContent").asString()).isEmpty();
        assertThat(visible("relancer-lecture")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "429 | TROP_DE_TENTATIVES | La limite de lectures est atteinte. Saisissez les valeurs ou relancez plus tard.",
        "500 | ERREUR_INTERNE | La lecture a échoué. Saisissez les valeurs ou relancez la lecture.",
    })
    void uneLectureEmpecheeMeneAuFormulaireVide(int statut, String code, String message) throws InterruptedException {
        chrome.eval("window.fetchOrigine = window.fetch; window.fetch = async (chemin, options) => String(chemin) === '/api/lectures' ? new Response(JSON.stringify({ erreur: 'x', code: '" + code + "' }), { status: " + statut + " }) : window.fetchOrigine(chemin, options); true");

        allerALaValidationAvecDeuxPhotos();

        assertThat(chrome.eval("document.getElementById('message-lecture').textContent").asString()).isEqualTo(message);
        assertThat(visible("relancer-lecture")).isTrue();
    }

    /** Photo A lue, retour, remplacement par la photo B, Continuer : le montant lu sur A ne part pas avec B, et la saisie reste. */
    @Test
    void ca29_desPhotosChangeesInvalidentLeMontantLuEtProposentLaRelance() throws InterruptedException {
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        modele.answer(Map.of("date", "2026-09-25", "fournisseur", "Unimage", "typeDepense", "Fournitures", "montantTotal", "304.68"));
        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        cliquer("continuer");
        chrome.waitUntil(NOMBRE_DE_PHOTOS_A_VALIDER + " === 1 && document.getElementById('validation').offsetParent !== null");
        assertThat(visible("relancer-lecture")).isFalse();

        cliquer("retour-photos");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies button').length === 1");
        chrome.eval("document.querySelector('#photos-choisies button').click(); true");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 0");
        chrome.chooseFiles("#importer", image2);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        cliquer("continuer");
        chrome.waitUntil("document.getElementById('validation').offsetParent !== null");

        assertThat(modele.calls).hasSize(1);
        assertThat(valeur("fournisseur")).isEqualTo("Unimage");
        assertThat(valeur("montantTotal")).isEqualTo("304,68");
        assertThat(visible("relancer-lecture")).isTrue();
        assertThat(chrome.eval("document.getElementById('message-lecture').textContent").asString())
                .isEqualTo("Les photos ont changé : les valeurs affichées viennent des précédentes. Vérifiez-les ou relancez la lecture.");

        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1 && document.getElementById('prise-de-vue').offsetParent !== null");
        assertThat(chrome.eval("window.envois").get(0).get("recu").get("montantTotalLuCents").isNull()).isTrue();
    }

    /** Sans changement de photos, le montant lu reste envoyé. */
    @Test
    void ca29_unRetourSansChangementDePhotosGardeLeMontantLu() throws InterruptedException {
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        modele.answer(Map.of("date", "2026-09-25", "fournisseur", "Unimage", "typeDepense", "Fournitures", "montantTotal", "304.68"));
        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        cliquer("continuer");
        chrome.waitUntil("document.getElementById('validation').offsetParent !== null");
        cliquer("retour-photos");
        chrome.waitUntil("document.getElementById('prise-de-vue').offsetParent !== null");
        cliquer("continuer");
        chrome.waitUntil("document.getElementById('validation').offsetParent !== null");

        assertThat(visible("relancer-lecture")).isFalse();
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1 && document.getElementById('prise-de-vue').offsetParent !== null");
        assertThat(chrome.eval("window.envois").get(0).get("recu").get("montantTotalLuCents").asInt()).isEqualTo(30468);
    }

    /** La clé reste la même après un changement de photos : si le premier envoi avait abouti, le renvoi est signalé en 409 et ne rend pas l'ancien reçu en silence. */
    @Test
    void ca18_desPhotosChangeesApresUneReponsePerdueSontSignalees() throws InterruptedException {
        long avant = recus();
        chrome.eval(ENREGISTRER_LES_ENVOIS);
        chrome.eval(PERDRE_LA_PREMIERE_REPONSE);
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur photos changées");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 1 && document.getElementById('message-validation').textContent !== ''");

        cliquer("retour-photos");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies button').length === 2");
        chrome.eval("document.querySelector('#photos-choisies button').click(); true");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 1");
        cliquer("continuer");
        chrome.waitUntil("document.getElementById('validation').offsetParent !== null");
        cliquer("valider");
        chrome.waitUntil("window.envois.length === 2 && document.getElementById('message-validation').textContent.includes('pas été appliquée')");

        JsonNode envois = chrome.eval("window.envois");
        assertThat(envois.get(1).get("cle").asString()).isEqualTo(envois.get(0).get("cle").asString());
        assertThat(envois.get(1).get("photos").asInt()).isEqualTo(1);
        assertThat(recus()).isEqualTo(avant + 1);
        assertThat(chrome.eval("document.getElementById('message-succes').textContent").asString()).isEmpty();
    }

    /** L'écran annonce l'alerte que le serveur figera : le contrôle porte le montant lu, tant qu'il vaut pour les photos affichées. */
    @Test
    void ca29_leControleEnvoieLeMontantLuPuisPlusApresUnChangementDePhotos() throws InterruptedException {
        chrome.eval("""
                window.controles = [];
                window.fetchAvantControle = window.fetch;
                window.fetch = async (chemin, options) => {
                  if (String(chemin) === '/api/recus/controles') {
                    window.controles.push(JSON.parse(options.body));
                  }
                  return window.fetchAvantControle(chemin, options);
                };
                true""");
        modele.answer(Map.of("date", "2026-09-25", "fournisseur", "Unimage", "typeDepense", "Fournitures", "montantTotal", "120.00"));
        chrome.chooseFiles("#importer", image1);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        cliquer("continuer");
        chrome.waitUntil("document.getElementById('validation').offsetParent !== null");
        chrome.waitUntil("window.controles.length >= 1 && document.querySelectorAll('#alertes li').length === 1");

        chrome.eval("const total = document.getElementById('montantTotal'); total.value = '40,00'; total.dispatchEvent(new Event('input', { bubbles: true })); true");
        chrome.waitUntil("window.controles.at(-1).valeurs.montantTotalCents === 4000");
        assertThat(chrome.eval("window.controles.at(-1).montantTotalLuCents").asInt()).isEqualTo(12000);
        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 1");
        assertThat(chrome.eval("document.querySelector('#alertes li').textContent").asString()).isEqualTo("Numéros d'inscription manquants : TPS, TVQ");

        cliquer("retour-photos");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies button').length === 1");
        chrome.eval("document.querySelector('#photos-choisies button').click(); true");
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 0");
        chrome.chooseFiles("#importer", image2);
        chrome.waitUntil(NOMBRE_DE_PHOTOS_AU_DEPART + " === 1");
        int avant = nombre("window.controles.length");
        cliquer("continuer");
        chrome.waitUntil("window.controles.length > " + avant + " && document.querySelectorAll('#alertes li').length === 0");

        assertThat(chrome.eval("'montantTotalLuCents' in window.controles.at(-1)").asBoolean()).isFalse();
    }

    /** Le corps exact du serveur est couvert par les tests sur serveur réel ; ici, le texte que chaque code donne à l'écran. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "400 | TROP_DE_PHOTOS | Ce reçu compte trop de photos. Rien n'a été enregistré.",
        "413 | TAILLE_EXCESSIVE | Les photos sont trop lourdes. Rien n'a été enregistré.",
        "400 | IMAGE_ILLISIBLE | Une photo est illisible. Rien n'a été enregistré.",
        "400 | IMAGE_TROP_GRANDE | Les dimensions d'une photo sont trop grandes. Rien n'a été enregistré.",
        "400 | PHOTO_MANQUANTE | Au moins une photo est requise. Rien n'a été enregistré.",
    })
    void ca70_cs11_chaqueRefusDUnePhotoDonneUnMessageClairSansPerdreLaSaisie(int statut, String code, String message) throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        saisirLesValeurs("Fournisseur refusé");
        chrome.eval("window.fetchOrigine = window.fetch; window.fetch = async () => new Response(JSON.stringify({ erreur: 'x', code: '" + code + "' }), { status: " + statut + " }); true");

        cliquer("valider");
        chrome.waitUntil("document.getElementById('message-validation').textContent !== ''");

        assertThat(chrome.eval("document.getElementById('message-validation').textContent").asString()).isEqualTo(message);
        assertThat(visible("validation")).isTrue();
        assertThat(valeur("fournisseur")).isEqualTo("Fournisseur refusé");
        assertThat(nombre(NOMBRE_DE_PHOTOS_A_VALIDER)).isEqualTo(2);
    }

    private static final String TEXTES_D_ALERTES = "Array.from(document.querySelectorAll('#alertes li')).map((alerte) => alerte.textContent).join(' | ')";

    private void signalerLaSaisie() {
        chrome.eval("document.getElementById('formulaire-recu').dispatchEvent(new Event('input', { bubbles: true })); true");
    }

    private void saisirUnRecuAAlertes(String fournisseur) {
        saisirLesValeurs(fournisseur);
        saisir("tps", "");
        saisir("montantTotal", "600,00");
        saisir("montantAvantTaxes", "500,00");
        signalerLaSaisie();
    }

    private String alertes() {
        return chrome.eval(TEXTES_D_ALERTES).asString();
    }

    @Test
    void ca80_lesAlertesSAffichentALaSaisieEtValiderResteActif() throws InterruptedException, IOException {
        long avant = recus();
        allerALaValidationAvecDeuxPhotos();

        saisirUnRecuAAlertes("Fournisseur alerté");
        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 3");
        capturer("validation-alertes.png");

        assertThat(alertes()).isEqualTo("Numéros d'inscription manquants : TPS, TVQ"
                + " | Pièce à vérifier : nom de l'acheteur, description, conditions de paiement | Écart de calcul : 100,00 $");
        assertThat(chrome.eval("document.getElementById('valider').disabled").asBoolean()).isFalse();
        assertThat(recus()).isEqualTo(avant);

        cliquer("valider");
        chrome.waitUntil("document.getElementById('prise-de-vue').offsetParent !== null");
        assertThat(recus()).isEqualTo(avant + 1);
        assertThat(nombre("document.querySelectorAll('#alertes li').length")).isZero();
    }

    @Test
    void ca80_desValeursCorrigeesRetirentLesAlertes() throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        saisirUnRecuAAlertes("Fournisseur corrigé ensuite");
        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 3");

        saisir("montantTotal", "35,00");
        saisir("montantAvantTaxes", "");
        signalerLaSaisie();

        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 0");
    }

    @Test
    void ca80_unTexteDeReponseResteDuTexteJamaisDuHtml() throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        chrome.eval("window.fetch = async () => new Response(JSON.stringify({ alertes: [{ code: 'X', message: '<img src=x onerror=\\'window.piege=1\\'>' }] }), { status: 200 }); true");

        saisirUnRecuAAlertes("Fournisseur piégé");
        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 1");

        assertThat(alertes()).isEqualTo("<img src=x onerror='window.piege=1'>");
        assertThat(nombre("document.querySelectorAll('#alertes img').length")).isZero();
    }

    @Test
    void ca80_unControleEnEchecLeDitSansBloquerLaValidation() throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        chrome.eval("window.fetchOrigine = window.fetch; window.fetch = async (chemin, options) => String(chemin) === '/api/recus/controles'"
                + " ? new Response('{}', { status: 500 }) : window.fetchOrigine(chemin, options); true");

        saisirUnRecuAAlertes("Fournisseur sans contrôle");
        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 1");

        assertThat(alertes()).isEqualTo("Les alertes n'ont pas pu être vérifiées. Vous pouvez tout de même valider.");
        assertThat(chrome.eval("document.getElementById('valider').disabled").asBoolean()).isFalse();
    }

    /** La première réponse attend {@code window.libererLePremier()} ; {@code window.premierLu} dit que la page l'a lue : elle l'a traitée à la tâche suivante. */
    private static final String RETENIR_LE_PREMIER_CONTROLE = """
            window.fetch = async () => {
              const numero = (window.controles = (window.controles || 0) + 1);
              if (numero === 1) {
                await new Promise((liberer) => { window.libererLePremier = liberer; });
                return { ok: true, status: 200, json: async () => {
                  window.premierLu = true;
                  return { alertes: [{ code: 'X', message: 'ancienne' }] };
                } };
              }
              return new Response(JSON.stringify({ alertes: [{ code: 'X', message: 'récente' }] }), { status: 200 });
            };
            true""";

    @Test
    void ca80_uneReponseEnRetardNeRemplacePasLaPlusRecente() throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        chrome.eval(RETENIR_LE_PREMIER_CONTROLE);
        saisirUnRecuAAlertes("Fournisseur en retard");
        chrome.waitUntil("!!window.libererLePremier");
        signalerLaSaisie();
        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 1");

        chrome.eval("window.libererLePremier(); true");
        chrome.waitUntil("window.premierLu === true");

        assertThat(alertes()).isEqualTo("récente");
    }

    @Test
    void ca80_uneReponseEnVolNeSurvitPasALAbandon() throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        chrome.eval(RETENIR_LE_PREMIER_CONTROLE);
        saisirUnRecuAAlertes("Fournisseur abandonné en vol");
        chrome.waitUntil("!!window.libererLePremier");

        cliquer("abandonner");
        chrome.eval("window.libererLePremier(); true");
        chrome.waitUntil("window.premierLu === true");

        assertThat(alertes()).isEmpty();
    }
}
