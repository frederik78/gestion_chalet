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
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Corps d'erreur et en-têtes de sécurité sur serveur réel, y compris pour ce que le framework ou Tomcat rejette avant l'application. */
@ServeurReelTest
class ErreursEtEnTetesServeurReelTest {

    /** La politique complète, recopiée ici à la main : toute source ajoutée, tierce ou non, doit faire échouer le test (CS09). */
    private static final String POLITIQUE_CONTENU = "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self'; "
            + "connect-src 'self'; manifest-src 'self'; worker-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    private static final Map<String, Map<String, String>> CORPS = Map.of(
            "REQUETE_INVALIDE", Map.of("erreur", "La requête est invalide.", "code", "REQUETE_INVALIDE"),
            "INTROUVABLE", Map.of("erreur", "Ressource introuvable.", "code", "INTROUVABLE"));

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
        assertThat(enTetes.get("content-security-policy")).as(contexte).containsExactly(POLITIQUE_CONTENU);
        assertThat(enTetes.get("x-content-type-options")).as(contexte).containsExactly("nosniff");
        assertThat(enTetes.get("x-frame-options")).as(contexte).containsExactly("DENY");
    }

    @Test
    void cs08_erreurInterneProvoqueeSansAucunDetail() {
        Reponse reponse = connecte().get("/api/essai/panne");

        assertThat(reponse.statut()).isEqualTo(500);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Une erreur interne est survenue.", "code", "ERREUR_INTERNE"));
        assertEnTetesDeSecurite(reponse.enTetes(), "panne");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void cs08_unePanneDeSerialisationDonne500EtUneLigneDeJournal(CapturedOutput journal) {
        Reponse reponse = connecte().get("/api/essai/non-serialisable");

        assertThat(reponse.statut()).isEqualTo(500);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Une erreur interne est survenue.", "code", "ERREUR_INTERNE"));
        assertThat(journal.getOut()).contains("Erreur interne : org.springframework.http.converter.HttpMessageNotWritableException")
                .doesNotContain(RouteEssaiResource.DETAIL_SECRET);
    }

    @Test
    void cs08_jsonCasseDonne400Generique() {
        Reponse reponse = connecte().appelAvecJeton("POST", "/api/essai/json", "{\"valeur\":");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
        assertEnTetesDeSecurite(reponse.enTetes(), "json casse");
    }

    @Test
    void cs08_typeInattenduDansLeJsonDonne400Generique() {
        Reponse reponse = connecte().appelAvecJeton("POST", "/api/essai/json", "{\"valeur\":{\"a\":[1]}}");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
    }

    @Test
    void cs08_champInvalideDonne400AvecLeChamp() {
        Reponse reponse = connecte().appelAvecJeton("POST", "/api/essai/json", "{}");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of(
                "erreur", "Certains champs sont invalides.",
                "code", "VALIDATION",
                "champs", List.of(Map.of("champ", "valeur", "code", "OBLIGATOIRE"))));
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

    /** Sur une route protégée, avec session : le multipart y est analysé. */
    private Reponse envoyerMultipartAvecSession(String typeDeContenu, String corps) {
        return envoyerMultipartAvecSession("/api/essai/photos", typeDeContenu, corps);
    }

    private Reponse envoyerMultipartAvecSession(String chemin, String typeDeContenu, String corps) {
        ClientEssai client = connecte();
        return client.envoyer(client.requete(chemin)
                .header("Content-Type", typeDeContenu)
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString(corps))
                .build());
    }

    @Test
    void cs08_multipartTronqueDonne400Generique() {
        Reponse reponse = envoyerMultipartAvecSession("multipart/form-data; boundary=x",
                "--x\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"a.jpg\"\r\n\r\ndebut sans fin");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
        assertEnTetesDeSecurite(reponse.enTetes(), "multipart tronque");
    }

    @Test
    void cs08_multipartSansFrontiereDonne400Generique() {
        Reponse reponse = envoyerMultipartAvecSession("multipart/form-data", "sans frontiere");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
        assertEnTetesDeSecurite(reponse.enTetes(), "multipart sans frontiere");
    }

    /** La connexion n'accepte qu'un formulaire : même avec une session, un multipart y est refusé. */
    @Test
    void cs08_contenuQuiNEstPasDuMultipartSurUneRouteJsonDonne415Generique() {
        Reponse reponse = envoyerMultipartAvecSession("/api/connexion", "multipart/form-data; boundary=x", "ceci n'est pas un multipart");

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
        assertEnTetesDeSecurite(reponse.enTetes(), "pas un multipart");
    }

    @Test
    void cs08_methodeNonPriseEnChargeDonne404Generique() {
        Reponse reponse = connecte().get("/api/connexion");

        assertThat(reponse.statut()).isEqualTo(404);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Ressource introuvable.", "code", "INTROUVABLE"));
        assertThat(reponse.enTete("Allow")).isNull();
    }

    /** La route existe, en POST seulement : le 405 de Spring MVC devient un 404 générique, sans révéler les méthodes permises. */
    @Test
    void cs08_uneMethodeNonPermiseSurUneRouteExistanteDonne404SansAllow() {
        Reponse reponse = connecte().appelAvecJeton("PUT", "/api/essai/json", "{}");

        assertThat(reponse.statut()).isEqualTo(404);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Ressource introuvable.", "code", "INTROUVABLE"));
        assertThat(reponse.enTete("Allow")).isNull();
    }

    @Test
    void cs08_corpsJsonAuDelaDeLaLimiteDonne413() {
        Reponse reponse = connecte().appelAvecJeton("POST", "/api/essai/json", "{\"valeur\":\"" + "x".repeat(70_000) + "\"}");

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
        assertEnTetesDeSecurite(reponse.enTetes(), "413");
    }

    @Test
    void cs08_corpsJsonALaLimiteResteTraite() {
        String debut = "{\"valeur\":\"v\",\"bourrage\":\"";
        String fin = "\"}";
        String corps = debut + "x".repeat(64 * 1024 - debut.length() - fin.length()) + fin;
        ClientEssai client = connecte();

        assertThat(client.appelAvecJeton("POST", "/api/essai/json", corps).statut()).isEqualTo(200);

        Reponse auDela = client.appelAvecJeton("POST", "/api/essai/json", corps.replace("\"}", "x\"}"));
        assertThat(auDela.statut()).isEqualTo(413);
        assertThat(auDela.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
    }

    @Test
    void cs08_formulaireDeConnexionBorneA64Ko() {
        String debut = "motDePasse=faux&bourrage=";
        String corps = debut + "x".repeat(64 * 1024 - debut.length());
        ClientEssai client = client();
        client.get("/actuator/health");

        Reponse aLaLimite = client.envoyer(client.requeteConnexion("x").POST(HttpRequest.BodyPublishers.ofString(corps)).build());
        assertThat(aLaLimite.statut()).isEqualTo(401);
        assertThat(aLaLimite.json()).isEqualTo(Map.of("erreur", "Identifiants invalides.", "code", "IDENTIFIANTS_INVALIDES"));

        Reponse auDela = client.envoyer(client.requeteConnexion("x").POST(HttpRequest.BodyPublishers.ofString(corps + "x")).build());
        assertThat(auDela.statut()).isEqualTo(413);
        assertThat(auDela.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
    }

    @Test
    void cs08_corpsJsonSansLongueurDeclareeEstAussiBorne() {
        ClientEssai client = connecte();
        String morceau = "x".repeat(70_000);
        ClientBrut.Reponse reponse = ClientBrut.envoyer(port, "POST /api/essai/json HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n"
                + "Content-Type: application/json\r\nX-XSRF-TOKEN: " + client.cookie("XSRF-TOKEN")
                + "\r\nCookie: XSRF-TOKEN=" + client.cookie("XSRF-TOKEN") + "; JSESSIONID=" + client.cookie("JSESSIONID") + "\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n" + Integer.toHexString(morceau.length()) + "\r\n" + morceau + "\r\n0\r\n\r\n");

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
    }

    /** Sans longueur déclarée, la borne ne peut se vérifier sans lire : la connexion refuse, et rien n'est compté. */
    @Test
    void cs08_formulaireDeConnexionSansLongueurDeclareeEstRefuse() {
        String jeton = UUID.randomUUID().toString();
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, "POST /api/connexion HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: application/x-www-form-urlencoded\r\nX-XSRF-TOKEN: " + jeton + "\r\nCookie: XSRF-TOKEN=" + jeton + "\r\n"
                + "Transfer-Encoding: chunked\r\n\r\n10000\r\nmotDePasse=", 5_000);

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
    }

    /** Le corps annoncé n'arrive jamais : sans session, personne ne doit l'attendre, y compris au-delà de la borne de 64 Ko. */
    @ParameterizedTest
    @CsvSource({"/, 1000", "/sw.js, 1000", "/, 400000000"})
    void cs01_unCorpsAnnonceSurUneRessourcePubliqueNEstPasLu(String chemin, long longueur) {
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, "GET " + chemin + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Length: " + longueur + "\r\n\r\n", 5_000);

        assertThat(reponse.statut()).isEqualTo(200);
    }

    /** Sans ce corps vide, Actuator lit lui-même un corps déclaré, même en GET : du JSON y serait chargé sans borne. */
    @ParameterizedTest
    @CsvSource({"text/plain, 1000", "application/json, 400000000"})
    void cs01_laSanteRepondSansLireUnCorpsAnnonce(String type, long longueur) {
        String recu = ClientBrut.lireJusqua(port, "GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nContent-Type: " + type
                + "\r\nContent-Length: " + longueur + "\r\n\r\n", "\"UP\"", 5_000);

        assertThat(recu).startsWith("HTTP/1.1 200");
    }

    /** Une route refusée sans session répond aussi sans attendre le corps, formulaire ou non. */
    @ParameterizedTest
    @CsvSource({"/api/essai/session", "/inconnue", "/accueil.html"})
    void cs01_unCorpsAnnonceSurUneRouteRefuseeNEstPasLu(String chemin) {
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, "GET " + chemin + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Length: 1000\r\n\r\n", 5_000);

        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));
    }

    /** Un multipart annoncé sur une ressource publique : Tomcat le lirait dès qu'un composant demande les paramètres. */
    @ParameterizedTest
    @CsvSource({"/actuator/health, multipart/form-data", "/, multipart/form-data", "/style.css, multipart/form-data",
            "/actuator/health, MULTIPART/FORM-DATA", "/, MULTIPART/FORM-DATA"})
    void cs08_unMultipartSansSessionSurUneRessourcePubliqueEstRefuseSansEtreLu(String chemin, String type) {
        String jeton = UUID.randomUUID().toString();
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, "GET " + chemin + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: " + type + "; boundary=x\r\nContent-Length: 56000000\r\n\r\n--x\r\n", 5_000);

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
    }

    @Test
    void cs08_unJsonSurLaConnexionDonne415() {
        Reponse reponse = client().appelAvecJeton("POST", "/api/connexion", "{\"motDePasse\":\"faux\"}");

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
    }

    /**
     * Requêtes écrites à la main : aucun client HTTP correct ne les enverrait. Rejetées par le pare-feu HTTP ou par Tomcat, avec le
     * code du tableau d'erreurs du PRD : 400 pour une requête illisible. TRACE, que Tomcat refuse en 405, suit la règle des méthodes : 404.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "barre encodee       | GET /api/%2F..%2Fetc%2Fpasswd HTTP/1.1 | 400 | REQUETE_INVALIDE",
            "antislash encode    | GET /api/%5C..%5Cetc HTTP/1.1          | 400 | REQUETE_INVALIDE",
            "octet nul           | GET /api/%00 HTTP/1.1                  | 400 | REQUETE_INVALIDE",
            "point-virgule       | GET /api/;jsessionid=1 HTTP/1.1        | 400 | REQUETE_INVALIDE",
            "double encodage     | GET /api/%252e%252e/ HTTP/1.1          | 400 | REQUETE_INVALIDE",
            "remontee            | GET /api/../../etc/passwd HTTP/1.1     | 400 | REQUETE_INVALIDE",
            "crochets interdits  | GET /api/[x] HTTP/1.1                  | 400 | REQUETE_INVALIDE",
            "methode inconnue    | PROPFIND /api/x HTTP/1.1               | 400 | REQUETE_INVALIDE",
            "methode trace       | TRACE /api/x HTTP/1.1                  | 404 | INTROUVABLE",
    })
    void cs08_requeteMalformeeRejeteeAvantLApplication(String nom, String ligne, int statut, String code) {
        ClientBrut.Reponse reponse = ClientBrut.envoyer(port, ligne + "\r\nHost: localhost\r\nConnection: close\r\n\r\n");

        assertThat(reponse.statut()).as(nom + " : " + reponse.corps()).isEqualTo(statut);
        assertThat(reponse.json()).as(nom).isEqualTo(CORPS.get(code));
        assertEnTetesDeSecurite(reponse.enTetes(), nom);
    }

    @Test
    void cs08_enTeteDemesureDonneUneErreurGenerique() {
        ClientBrut.Reponse reponse = ClientBrut.envoyer(port,
                "GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nX-Grand: " + "x".repeat(20_000) + "\r\nConnection: close\r\n\r\n");

        assertThat(reponse.statut()).as(reponse.corps()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(REQUETE_INVALIDE);
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

    private static String debutMultipartSansSession(String chemin) {
        return debutMultipartSansSession(chemin, "multipart/form-data");
    }

    private static String debutMultipartSansSession(String chemin, String type) {
        String jeton = UUID.randomUUID().toString();
        return "POST " + chemin + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: " + type + "; boundary=x\r\nContent-Length: 56000000\r\n"
                + "X-XSRF-TOKEN: " + jeton + "\r\nCookie: XSRF-TOKEN=" + jeton + "\r\n\r\n"
                + "--x\r\nContent-Disposition: form-data; name=\"photos\"; filename=\"a.jpg\"\r\n\r\ndebut";
    }

    /** Un type de contenu ne dépend pas de la casse : en majuscules, il ne doit pas échapper au refus. */
    @ParameterizedTest
    @ValueSource(strings = {"multipart/form-data", "MULTIPART/FORM-DATA"})
    void cs08_unMultipartSansSessionEstRefuseSansEtreLu(String type) {
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, debutMultipartSansSession("/api/connexion", type), 5_000);

        assertThat(reponse.statut()).isEqualTo(415);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Format non accepté.", "code", "FORMAT_NON_ACCEPTE"));
        assertEnTetesDeSecurite(reponse.enTetes(), "multipart sans session");
    }

    @Test
    void cs01_unMultipartSansSessionSurUneRouteProtegeeResteUn401() {
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, debutMultipartSansSession("/api/essai/mutation/1"), 5_000);

        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));
    }

    private static String debutSansLaFin(String methode, String chemin, String typeDeContenu, long longueur, String cookies, String jeton) {
        return methode + " " + chemin + " HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: " + typeDeContenu + "\r\nContent-Length: " + longueur + "\r\n"
                + "X-XSRF-TOKEN: " + jeton + "\r\nCookie: " + cookies + "\r\n\r\na=1";
    }

    /**
     * Le corps annoncé n'arrive jamais : si un filtre tentait de le lire avant la sécurité, le serveur ne répondrait pas à temps.
     * 1,5 Mo reste sous la limite de 2 Mo que Tomcat applique aux formulaires quand un filtre appelle {@code getParameter}.
     */
    @ParameterizedTest(name = "{0} {1} octets")
    @CsvSource({"PUT, 400000000", "PATCH, 400000000", "DELETE, 400000000", "POST, 400000000", "POST, 1500000", "PUT, 1500000"})
    void cs01_unFormulaireSansSessionEstRefuseSansEtreLu(String methode, long longueur) {
        String jeton = UUID.randomUUID().toString();
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, debutSansLaFin(methode, "/api/essai/mutation/1",
                "application/x-www-form-urlencoded", longueur, "XSRF-TOKEN=" + jeton, jeton), 5_000);

        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));
    }

    /** Sur la route publique : la sécurité laisse passer, la borne de 64 Ko refuse sans lire. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /api/connexion, 400000000", "POST, /api/connexion, 1500000"})
    void cs08_unFormulaireTropGrosSurLaRoutePubliqueEstRefuseSansEtreLu(String methode, String chemin, long longueur) {
        String jeton = UUID.randomUUID().toString();
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, debutSansLaFin(methode, chemin,
                "application/x-www-form-urlencoded", longueur, "XSRF-TOKEN=" + jeton, jeton), 5_000);

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void cs08_unFormulaireTropGrosAvecSessionEstRefuseSansEtreLu(String methode) {
        ClientEssai client = connecte();
        String cookies = "JSESSIONID=" + client.cookie("JSESSIONID") + "; XSRF-TOKEN=" + client.cookie("XSRF-TOKEN");
        ClientBrut.Reponse reponse = ClientBrut.envoyerSansLaFinDuCorps(port, debutSansLaFin(methode, "/api/essai/mutation/1",
                "application/x-www-form-urlencoded", 400_000_000L, cookies, client.cookie("XSRF-TOKEN")), 5_000);

        assertThat(reponse.statut()).isEqualTo(413);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "La requête est trop volumineuse.", "code", "TAILLE_EXCESSIVE"));
    }
}
