package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientBrut;
import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.RouteEssaiResource;
import ca.gestionchalet.support.ServeurReelTest;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Corps d'erreur et en-têtes de sécurité sur serveur réel, y compris pour ce que le framework ou Tomcat rejette avant l'application. */
@ServeurReelTest
class ErreursEtEnTetesServeurReelTest {

    private static final Map<String, String> REQUETE_INVALIDE = Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE");

    @LocalServerPort
    int port;

    private ClientEssai client() {
        return new ClientEssai(port, "source-" + UUID.randomUUID());
    }

    private ClientEssai connecte() {
        ClientEssai client = client();
        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);
        return client;
    }

    private static void assertEnTetesDeSecurite(Map<String, List<String>> enTetes, String contexte) {
        String csp = String.join(",", enTetes.getOrDefault("content-security-policy", List.of()));
        assertThat(csp).as(contexte).contains("default-src 'none'", "script-src 'self'", "frame-ancestors 'none'")
                .doesNotContain("unsafe-inline", "unsafe-eval", "http:", "https:", "*");
        assertThat(enTetes.get("x-content-type-options")).as(contexte).containsExactly("nosniff");
        assertThat(enTetes.get("x-frame-options")).as(contexte).containsExactly("DENY");
    }

    private static void assertCorpsGenerique(String corps, String contexte) {
        assertThat(corps).as(contexte).doesNotContainIgnoringCase("tomcat").doesNotContain("Exception", "<html", "java.", "at ca.", "ca.gestionchalet", RouteEssaiResource.DETAIL_SECRET);
    }

    @Test
    void cs08_erreurInterneProvoqueeSansAucunDetail() {
        Reponse reponse = connecte().get("/api/essai/panne");

        assertThat(reponse.statut()).isEqualTo(500);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Une erreur interne est survenue.", "code", "ERREUR_INTERNE"));
        assertCorpsGenerique(reponse.corps(), "panne");
        assertEnTetesDeSecurite(reponse.enTetes(), "panne");
    }

    @Test
    void cs08_jsonCasseDonne400Generique() {
        Reponse reponse = client().appelAvecJeton("POST", "/api/connexion", "{\"motDePasse\":");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
        assertEnTetesDeSecurite(reponse.enTetes(), "json casse");
    }

    @Test
    void cs08_typeInattenduDansLeJsonDonne400Generique() {
        Reponse reponse = client().appelAvecJeton("POST", "/api/connexion", "{\"motDePasse\":{\"a\":[1]}}");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
    }

    @Test
    void cs08_formatNonAccepteDonne415Generique() {
        ClientEssai client = client();
        client.get("/actuator/health");
        Reponse reponse = client.envoyer(client.requete("/api/connexion")
                .header("Content-Type", "text/plain")
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString("x"))
                .build());

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
        assertEnTetesDeSecurite(reponse.enTetes(), "415");
    }

    private Reponse envoyerMultipart(String typeDeContenu, String corps) {
        ClientEssai client = client();
        client.get("/actuator/health");
        return client.envoyer(client.requete("/api/connexion")
                .header("Content-Type", typeDeContenu)
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString(corps))
                .build());
    }

    @Test
    void cs08_multipartTronqueDonne400Generique() {
        Reponse reponse = envoyerMultipart("multipart/form-data; boundary=x",
                "--x\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"a.jpg\"\r\n\r\ndebut sans fin");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
        assertEnTetesDeSecurite(reponse.enTetes(), "multipart tronque");
    }

    @Test
    void cs08_multipartSansFrontiereDonneUneErreurGenerique() {
        Reponse reponse = envoyerMultipart("multipart/form-data", "sans frontiere");

        assertThat(reponse.statut()).as(reponse.corps()).isIn(400, 415);
        assertThat(reponse.json().keySet()).isEqualTo(Set.of("erreur", "code"));
        assertEnTetesDeSecurite(reponse.enTetes(), "multipart sans frontiere");
    }

    @Test
    void cs08_contenuQuiNEstPasDuMultipartDonneUneErreurGenerique() {
        Reponse reponse = envoyerMultipart("multipart/form-data; boundary=x", "ceci n'est pas un multipart");

        assertThat(reponse.statut()).as(reponse.corps()).isIn(400, 415);
        assertThat(reponse.json().keySet()).isEqualTo(Set.of("erreur", "code"));
        assertEnTetesDeSecurite(reponse.enTetes(), "pas un multipart");
    }

    @Test
    void cs08_methodeNonPriseEnChargeDonne404Generique() {
        Reponse reponse = connecte().get("/api/connexion");

        assertThat(reponse.statut()).isEqualTo(404);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Ressource introuvable.", "code", "INTROUVABLE"));
        assertThat(reponse.enTete("Allow")).isNull();
    }

    @Test
    void cs08_corpsJsonAuDelaDeLaLimiteDonne413() {
        Reponse reponse = client().appelAvecJeton("POST", "/api/connexion", "{\"motDePasse\":\"" + "x".repeat(70_000) + "\"}");

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
        assertEnTetesDeSecurite(reponse.enTetes(), "413");
    }

    @Test
    void cs08_corpsJsonALaLimiteResteTraite() {
        String debut = "{\"motDePasse\":\"faux\",\"bourrage\":\"";
        String fin = "\"}";
        String corps = debut + "x".repeat(64 * 1024 - debut.length() - fin.length()) + fin;

        assertThat(client().appelAvecJeton("POST", "/api/connexion", corps).statut()).isEqualTo(401);
        assertThat(client().appelAvecJeton("POST", "/api/connexion", corps.replace("\"}", "x\"}")).statut()).isEqualTo(413);
    }

    @Test
    void cs08_corpsJsonSansLongueurDeclareeEstAussiBorne() {
        ClientBrut.Reponse reponse = ClientBrut.envoyer(port, jetonEtCorpsFragmente());

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
    }

    private String jetonEtCorpsFragmente() {
        String jeton = UUID.randomUUID().toString();
        String morceau = "x".repeat(70_000);
        String corps = "{\"motDePasse\":\"" + morceau + "\"}";
        return "POST /api/connexion HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                + "Content-Type: application/json\r\nX-XSRF-TOKEN: " + jeton + "\r\nCookie: XSRF-TOKEN=" + jeton + "\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n"
                + Integer.toHexString(corps.length()) + "\r\n" + corps + "\r\n0\r\n\r\n";
    }

    /** Requêtes écrites à la main : aucun client HTTP correct ne les enverrait. Rejetées par le pare-feu HTTP ou par Tomcat. */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "barre encodee       | GET /api/%2F..%2Fetc%2Fpasswd HTTP/1.1",
            "antislash encode    | GET /api/%5C..%5Cetc HTTP/1.1",
            "octet nul           | GET /api/%00 HTTP/1.1",
            "point-virgule       | GET /api/;jsessionid=1 HTTP/1.1",
            "double encodage     | GET /api/%252e%252e/ HTTP/1.1",
            "remontee            | GET /api/../../etc/passwd HTTP/1.1",
            "crochets interdits  | GET /api/[x] HTTP/1.1",
            "methode inconnue    | PROPFIND /api/x HTTP/1.1",
            "methode trace       | TRACE /api/x HTTP/1.1",
    })
    void cs08_requeteMalformeeRejeteeAvantLApplication(String nom, String ligne) {
        ClientBrut.Reponse reponse = ClientBrut.envoyer(port, ligne + "\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(reponse.statut()).as(nom + " : " + reponse.corps()).isBetween(400, 499);
        assertThat(reponse.json().keySet()).as(nom).isEqualTo(Set.of("erreur", "code"));
        assertCorpsGenerique(reponse.corps(), nom);
        assertEnTetesDeSecurite(reponse.enTetes(), nom);
    }

    @Test
    void cs08_enTeteDemesureDonneUneErreurGenerique() {
        ClientBrut.Reponse reponse = ClientBrut.envoyer(port,
                "GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nX-Grand: " + "x".repeat(20_000) + "\r\nConnection: close\r\n\r\n");

        assertThat(reponse.statut()).as(reponse.corps()).isBetween(400, 499);
        assertThat(reponse.json().keySet()).isEqualTo(Set.of("erreur", "code"));
        assertEnTetesDeSecurite(reponse.enTetes(), "en-tete demesure");
    }

    @Test
    void cs08_aucuneVersionDansLesEnTetes() {
        Reponse reponse = client().get("/actuator/health");

        assertThat(reponse.enTete("Server")).isNull();
        assertThat(reponse.enTete("X-Powered-By")).isNull();
    }

    @Test
    void cs09_lesEnTetesSontPresentsSurToutesLesReponses() {
        ClientEssai client = client();
        assertEnTetesDeSecurite(client.get("/actuator/health").enTetes(), "200");
        assertEnTetesDeSecurite(client.get("/api/essai/session").enTetes(), "401");
        assertEnTetesDeSecurite(client.appelSansJeton("POST", "/api/connexion", "{}", null).enTetes(), "403");
        assertEnTetesDeSecurite(client.connecter("faux").enTetes(), "401 connexion");
        assertEnTetesDeSecurite(connecte().get("/inconnue").enTetes(), "404");
    }

    @Test
    void cs09_lesReponsesDeLApiNeSontJamaisEnCache() {
        ClientEssai client = client();
        assertThat(client.get("/api/essai/session").enTete("Cache-Control")).isEqualTo("no-store");
        assertThat(client.appelSansJeton("POST", "/api/connexion", "{}", null).enTete("Cache-Control")).isEqualTo("no-store");
        assertThat(client.connecter("faux").enTete("Cache-Control")).isEqualTo("no-store");
        ClientEssai session = connecte();
        assertThat(session.get("/api/essai/session").enTete("Cache-Control")).isEqualTo("no-store");
        assertThat(session.get("/api/inconnue").enTete("Cache-Control")).isEqualTo("no-store");
        assertThat(session.get("/api/essai/panne").enTete("Cache-Control")).isEqualTo("no-store");
    }
}
