package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ChromeSession;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
@TestPropertySource(properties = "recus.securite.cookie-securise=false")
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

    @TempDir
    Path dossier;

    private ChromeSession chrome;
    private Path image1;
    private Path image2;

    @BeforeEach
    void ouvrirEtSeConnecter() throws IOException, InterruptedException {
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

    /** L'écran laisse passer ce fichier, d'après son extension : c'est le serveur qui lit son contenu et le refuse. */
    @Test
    void ca69_cs10_unFichierDeguiseEstRefuseParLeServeurEtLaSaisieEstConservee() throws InterruptedException, IOException {
        long avant = recus();
        Path deguise = Files.writeString(dossier.resolve("deguise.png"), "<!doctype html><html><script>alert(1)</script></html>");
        chrome.chooseFiles("#importer", deguise);
        chrome.waitUntil("document.querySelectorAll('#photos-choisies figure').length === 1");
        cliquer("continuer");
        chrome.waitUntil("document.querySelectorAll('#photos-a-valider figure').length === 1");
        saisirLesValeurs("Fournisseur déguisé");

        cliquer("valider");
        chrome.waitUntil("document.getElementById('message-validation').textContent !== ''");
        capturer("validation-format-refuse.png");

        assertThat(chrome.eval("document.getElementById('message-validation').textContent").asString())
                .isEqualTo("Une photo n'est pas une image JPEG ou PNG. Rien n'a été enregistré.");
        assertThat(visible("validation")).isTrue();
        assertThat(valeur("fournisseur")).isEqualTo("Fournisseur déguisé");
        assertThat(valeur("montantTotal")).isEqualTo("304,68");
        assertThat(recus()).isEqualTo(avant);
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

    @Test
    void ca80_uneReponseEnRetardNeRemplacePasLaPlusRecenteEtNeSurvitPasALAbandon() throws InterruptedException {
        allerALaValidationAvecDeuxPhotos();
        chrome.eval("""
                window.fetch = async (chemin, options) => {
                  const numero = (window.controles = (window.controles || 0) + 1);
                  if (numero === 1) {
                    await new Promise((liberer) => { window.libererLePremier = liberer; });
                    return new Response(JSON.stringify({ alertes: [{ code: 'X', message: 'ancienne' }] }), { status: 200 });
                  }
                  return new Response(JSON.stringify({ alertes: [{ code: 'X', message: 'récente' }] }), { status: 200 });
                };
                true""");
        saisirUnRecuAAlertes("Fournisseur en retard");
        chrome.waitUntil("!!window.libererLePremier");
        signalerLaSaisie();
        chrome.waitUntil("document.querySelectorAll('#alertes li').length === 1");

        chrome.eval("window.libererLePremier(); true");
        Thread.sleep(200);
        assertThat(alertes()).isEqualTo("récente");

        cliquer("abandonner");
        assertThat(nombre("document.querySelectorAll('#alertes li').length")).isZero();
    }
}
