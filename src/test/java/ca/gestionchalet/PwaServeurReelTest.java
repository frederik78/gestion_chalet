package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.ServeurReelTest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** PWA vérifiée en HTTP et par l'analyse de tous les fichiers servis, qu'une issue suivante en ajoute ou non. */
@ServeurReelTest
class PwaServeurReelTest {

    private static final List<String> RESSOURCES_DE_CONNEXION = List.of(
            "/", "/index.html", "/connexion.js", "/commun.js", "/style.css", "/manifest.webmanifest", "/sw.js",
            "/icones/icone-192.png", "/icones/icone-512.png");

    @LocalServerPort
    int port;

    private ClientEssai client() {
        return new ClientEssai(port, "source-" + UUID.randomUUID());
    }

    @Test
    void ca83_leManifesteDonneUnNomDesIconesEtUnAffichageAutonome() {
        Reponse reponse = client().get("/manifest.webmanifest");

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.enTete("Content-Type")).startsWith("application/manifest+json");
        Map<String, Object> manifeste = reponse.json();
        assertThat(manifeste.get("name")).isEqualTo("Gestion de chalet");
        assertThat(manifeste.get("short_name")).isEqualTo("Chalet");
        assertThat(manifeste.get("display")).isEqualTo("standalone");
        assertThat(manifeste.get("start_url")).isEqualTo("/");
        assertThat(manifeste.get("scope")).isEqualTo("/");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ca83_lesIconesDuManifesteSontServiesAuxDimensionsAnnoncees() throws IOException, InterruptedException {
        List<Map<String, String>> icones = (List<Map<String, String>>) client().get("/manifest.webmanifest").json().get("icons");

        assertThat(icones).extracting(icone -> icone.get("sizes")).containsExactly("192x192", "512x512");
        for (Map<String, String> icone : icones) {
            HttpResponse<byte[]> reponse = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + icone.get("src"))).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertThat(reponse.statusCode()).isEqualTo(200);
            assertThat(icone.get("type")).isEqualTo("image/png");
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(reponse.body()));
            assertThat(image.getWidth() + "x" + image.getHeight()).isEqualTo(icone.get("sizes"));
        }
    }

    @Test
    void cs01_lesRessourcesDeLaPageDeConnexionSontPubliques() {
        for (String chemin : RESSOURCES_DE_CONNEXION) {
            assertThat(client().get(chemin).statut()).as(chemin).isEqualTo(200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/accueil.html", "/accueil.js"})
    void cs01_leResteDeLaPwaExigeUneSession(String chemin) {
        ClientEssai client = client();
        Reponse sansSession = client.get(chemin);
        assertThat(sansSession.statut()).isEqualTo(401);
        assertThat(sansSession.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));

        client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(client.get(chemin).statut()).isEqualTo(200);
    }

    @Test
    void cs01_laPageDeConnexionPoseLeCookieCsrfSansOuvrirDeSession() {
        ClientEssai client = client();

        Reponse reponse = client.get("/");

        assertThat(client.cookie("XSRF-TOKEN")).isNotNull();
        assertThat(client.cookie("JSESSIONID")).isNull();
        assertThat(reponse.corps()).contains("<form");
    }

    private static List<Resource> fichiersServis() throws IOException {
        return List.of(new PathMatchingResourcePatternResolver().getResources("classpath*:static/**/*.*"));
    }

    private static String texte(Resource fichier) throws IOException {
        return fichier.getContentAsString(StandardCharsets.UTF_8);
    }

    private static boolean estTexte(Resource fichier) {
        String nom = String.valueOf(fichier.getFilename());
        return nom.matches(".*\\.(html|js|css|webmanifest)");
    }

    @Test
    void cs28_aucunFichierServiNeVisUnDomaineTiers() throws IOException {
        List<String> tiers = new ArrayList<>();
        Pattern adresseAbsolue = Pattern.compile("(?i)(https?:)?//[a-z0-9.-]+\\.[a-z]{2,}|url\\(\\s*['\"]?(https?:|//)|@import");
        List<Resource> fichiers = fichiersServis();
        assertThat(fichiers).extracting(Resource::getFilename).contains("index.html", "sw.js", "manifest.webmanifest", "accueil.html");
        for (Resource fichier : fichiers) {
            if (estTexte(fichier)) {
                Matcher trouve = adresseAbsolue.matcher(texte(fichier));
                while (trouve.find()) {
                    tiers.add(fichier.getFilename() + " : " + trouve.group());
                }
            }
        }
        assertThat(tiers).isEmpty();
    }

    @Test
    void cs28_lesPagesNOntNiScriptNiStyleEnLigne() throws IOException {
        Pattern enLigne = Pattern.compile("(?is)<script(?![^>]*\\bsrc=)[^>]*>|<style\\b|\\son[a-z]+\\s*=|\\sstyle\\s*=|javascript:");
        for (Resource fichier : fichiersServis()) {
            if (String.valueOf(fichier.getFilename()).endsWith(".html")) {
                assertThat(enLigne.matcher(texte(fichier)).find()).as(fichier.getFilename()).isFalse();
            }
        }
    }

    /** La PWA n'a pas de mode hors ligne : aucune API de cache n'est appelée pour écrire, donc rien de /api ni aucune photo n'est retenu. */
    @Test
    void cs28_laLogiqueDeCacheNeRetientRien() throws IOException {
        Pattern ecritureDeCache = Pattern.compile("caches\\.open|caches\\.match|\\.put\\(|\\.addAll\\(|\\.add\\(|localStorage|sessionStorage|indexedDB");
        for (Resource fichier : fichiersServis()) {
            if (String.valueOf(fichier.getFilename()).endsWith(".js")) {
                assertThat(ecritureDeCache.matcher(texte(fichier)).find()).as(fichier.getFilename()).isFalse();
            }
        }
    }

    @Test
    void cs28_leServiceWorkerNIntercepteAucuneRequete() {
        String script = client().get("/sw.js").corps();

        assertThat(script).doesNotContain("'fetch'", "\"fetch\"", "onfetch");
        assertThat(script).contains("caches.delete");
    }
}
