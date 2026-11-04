package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.gestionchalet.service.GoogleTokenService;
import ca.gestionchalet.support.GoogleStub;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

/** Autorisation Google de bout en bout : serveur réel, session, base temporaire, et un serveur local à la place de Google. */
@RealServerTest
@ExtendWith(OutputCaptureExtension.class)
class GoogleAuthorizationRealServerTest {

    private static final GoogleStub GOOGLE = demarrerGoogle();

    private static GoogleStub demarrerGoogle() {
        try {
            return new GoogleStub();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void adressesDeGoogle(DynamicPropertyRegistry registre) {
        registre.add("spring.security.oauth2.client.provider.google.authorization-uri", GOOGLE::adresseConsentement);
        registre.add("spring.security.oauth2.client.provider.google.token-uri", GOOGLE::adresseJeton);
        registre.add("recus.google.delai-appel", () -> "3s");
    }

    @AfterAll
    static void arreterGoogle() {
        GOOGLE.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

    @Autowired
    GoogleTokenService jetons;

    private TestClient client;

    @BeforeEach
    void repartirDeZero() {
        base.update("delete from photo");
        base.update("delete from recu");
        base.update("delete from autorisation_google");
        GOOGLE.reinitialiser();
        client = connecte();
    }

    private TestClient connecte() {
        TestClient connecte = new TestClient(port, "source-" + UUID.randomUUID());
        assertThat(connecte.connecter(TestDoublesConfiguration.MOT_DE_PASSE).statut()).isEqualTo(204);
        return connecte;
    }

    private Reponse valider() {
        return RecuRequests.valider(client, RecuRequests.corps(RecuRequests.valeurs()), RecuRequests.png());
    }

    @Test
    void ca64_sansAutorisationUnRecuValideEstEnregistreBloqueEtLEtatEstAbsente() {
        Reponse reponse = valider();

        assertThat(reponse.statut()).isEqualTo(201);
        assertThat(reponse.json()).containsEntry("etatCopie", "BLOQUEE").containsEntry("motifBlocage", "AUTORISATION_GOOGLE");
        assertThat(base.queryForObject("select motif_blocage from recu where id = ?", String.class, reponse.json().get("id")))
                .isEqualTo("AUTORISATION_GOOGLE");
        Reponse etat = client.get("/api/google/etat");
        assertThat(etat.statut()).isEqualTo(200);
        assertThat(etat.corps()).isEqualTo("{\"etat\":\"ABSENTE\"}");
    }

    /** Les paramètres décodés d'une redirection reçue. */
    private static Map<String, String> parametres(String adresse) {
        return UriComponentsBuilder.fromUri(URI.create(adresse)).build(true).getQueryParams().toSingleValueMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, parametre -> URLDecoder.decode(parametre.getValue(), StandardCharsets.UTF_8)));
    }

    @Test
    void ca68_laDemandeNeReclameQueLesDroitsDeLaSection76() {
        Reponse reponse = client.get("/api/google/autorisation");

        assertThat(reponse.statut()).isEqualTo(302);
        String adresse = reponse.enTete("Location");
        assertThat(adresse).startsWith(GOOGLE.adresseConsentement() + "?");
        Map<String, String> demande = parametres(adresse);
        assertThat(demande.get("scope").split(" ")).containsExactlyInAnyOrder(
                "https://www.googleapis.com/auth/spreadsheets", "https://www.googleapis.com/auth/drive.file");
        assertThat(demande).containsEntry("response_type", "code").containsEntry("client_id", "client-essai")
                .containsEntry("redirect_uri", "http://localhost:" + port + "/api/google/rappel")
                .containsEntry("code_challenge_method", "S256")
                .containsEntry("access_type", "offline").containsEntry("prompt", "consent")
                .doesNotContainKey("include_granted_scopes");
        assertThat(demande.get("state")).isNotBlank();
        assertThat(demande.get("code_challenge")).isNotBlank();
        assertThat(GOOGLE.requetes()).isEmpty();
    }

    @Test
    void ca65_sansSessionLaDemandeEtLeRappelSontRefuses() {
        TestClient anonyme = new TestClient(port, "source-" + UUID.randomUUID());

        assertThat(anonyme.get("/api/google/autorisation").statut()).isEqualTo(401);
        assertThat(anonyme.get("/api/google/rappel?code=c&state=s").statut()).isEqualTo(401);
        assertThat(GOOGLE.requetes()).isEmpty();
    }

    private String etat() {
        return (String) client.get("/api/google/etat").json().get("etat");
    }

    private String etatCopie(Object id) {
        return base.queryForObject("select etat_copie from recu where id = ?", String.class, id);
    }

    /** Suit la demande d'autorisation et revient par le rappel, comme le navigateur après le consentement ; rend la dernière redirection. */
    private Reponse autoriser() {
        String state = parametres(client.get("/api/google/autorisation").enTete("Location")).get("state");
        return rappel("/api/google/rappel?code=code-temoin&state=" + state);
    }

    /** Le filtre de Spring Security renvoie d'abord à l'adresse de rappel sans paramètre, qui mène à la PWA. */
    private Reponse rappel(String chemin) {
        Reponse reponse = client.get(chemin);
        assertThat(reponse.statut()).isEqualTo(302);
        String suite = reponse.enTete("Location");
        return suite.startsWith("http://localhost:" + port + "/api/google/rappel")
                ? client.get(suite.substring(("http://localhost:" + port).length()))
                : reponse;
    }

    @Test
    void ca65_leRappelValideRendLEtatValideEtRepriseLesRecusBloques() throws Exception {
        Object bloque = valider().json().get("id");
        String demande = client.get("/api/google/autorisation").enTete("Location");
        Map<String, String> parametres = parametres(demande);

        Reponse fin = rappel("/api/google/rappel?code=code-temoin&state=" + parametres.get("state"));

        assertThat(fin.statut()).isEqualTo(302);
        assertThat(fin.enTete("Location")).isEqualTo("/");
        assertThat(etat()).isEqualTo("VALIDE");
        assertThat(etatCopie(bloque)).isEqualTo("A_COPIER");
        assertThat(base.queryForObject("select motif_blocage from recu where id = ?", String.class, bloque)).isNull();
        GoogleStub.Requete echange = GOOGLE.requetes().getFirst();
        assertThat(GOOGLE.requetes()).hasSize(1);
        assertThat(echange.chemin()).isEqualTo("/jeton");
        assertThat(echange.parametres()).containsEntry("grant_type", "authorization_code").containsEntry("code", "code-temoin");
        String preuve = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(echange.parametres().get("code_verifier").getBytes(StandardCharsets.US_ASCII)));
        assertThat(preuve).isEqualTo(parametres.get("code_challenge"));
        assertThat(valider().json()).containsEntry("etatCopie", "A_COPIER").containsEntry("motifBlocage", null);
    }

    private List<GoogleStub.Requete> renouvellements() {
        return GOOGLE.requetes().stream().filter(requete -> "refresh_token".equals(requete.parametres().get("grant_type"))).toList();
    }

    private Map<String, Object> ligneAutorisation() {
        return base.queryForMap("select * from autorisation_google");
    }

    @Test
    void ca65_lesCopiesSuivantesRenouvellentLeJetonSansNouvelleAutorisation() {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        // Comme Google : le renouvellement ne redonne pas de jeton de renouvellement, et l'ancien reste bon.
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, null);

        assertThat(jetons.accessToken()).contains(GoogleStub.JETON_ACCES);
        assertThat(jetons.accessToken()).contains(GoogleStub.JETON_ACCES);

        assertThat(renouvellements()).hasSize(2).allSatisfy(requete ->
                assertThat(requete.parametres()).containsEntry("refresh_token", GoogleStub.JETON_RENOUVELLEMENT));
        assertThat(etat()).isEqualTo("VALIDE");
    }

    @Test
    void ca65_unJetonEncoreValableNeDemandeRienAGoogle() {
        autoriser();

        assertThat(jetons.accessToken()).contains(GoogleStub.JETON_ACCES);

        assertThat(renouvellements()).isEmpty();
    }

    @Test
    void ca66_invalidGrantPasseARenouvelerBloqueLesRecusEtUneNouvelleAutorisationLesReprend() {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        Object enAttente = valider().json().get("id");
        assertThat(etatCopie(enAttente)).isEqualTo("A_COPIER");
        GOOGLE.repondre(400, "{\"error\":\"invalid_grant\"}");

        assertThat(jetons.accessToken()).isEmpty();

        assertThat(etat()).isEqualTo("A_RENOUVELER");
        assertThat(etatCopie(enAttente)).isEqualTo("BLOQUEE");
        assertThat(base.queryForObject("select motif_blocage from recu where id = ?", String.class, enAttente)).isEqualTo("AUTORISATION_GOOGLE");
        assertThat(ligneAutorisation()).containsEntry("jeton_renouvellement_chiffre", null).containsEntry("jeton_acces_chiffre", null);
        assertThat(jetons.accessToken()).isEmpty();
        Reponse pendant = valider();
        assertThat(pendant.statut()).isEqualTo(201);
        assertThat(pendant.json()).containsEntry("etatCopie", "BLOQUEE").containsEntry("motifBlocage", "AUTORISATION_GOOGLE");

        GOOGLE.reinitialiser();
        autoriser();

        assertThat(etat()).isEqualTo("VALIDE");
        assertThat(etatCopie(enAttente)).isEqualTo("A_COPIER");
        assertThat(etatCopie(pendant.json().get("id"))).isEqualTo("A_COPIER");
        assertThat(base.queryForObject("select count(*) from recu", Long.class)).isEqualTo(2);
    }

    @Test
    void ca66_uneReponseGoogleEnPanneNeRetireNiLAutorisationNiLesRecus() {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        Object enAttente = valider().json().get("id");
        GOOGLE.repondre(503, "indisponible");

        assertThatThrownBy(() -> jetons.accessToken()).isInstanceOf(OAuth2AuthorizationException.class);

        assertThat(etat()).isEqualTo("VALIDE");
        assertThat(etatCopie(enAttente)).isEqualTo("A_COPIER");
    }

    @Test
    void ca66_unGoogleMuetNeBloqueNiLAppelNiLAutorisation() {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        GOOGLE.retarder(5000);

        assertThatThrownBy(() -> jetons.accessToken()).isInstanceOf(OAuth2AuthorizationException.class);

        assertThat(etat()).isEqualTo("VALIDE");
    }

    @Test
    void ca66_desDroitsRetiresAuRenouvellementPassentARenouveler() {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        Object enAttente = valider().json().get("id");
        GOOGLE.repondreJeton(3600, GoogleStub.SHEETS, null);

        assertThat(jetons.accessToken()).isEmpty();

        assertThat(etat()).isEqualTo("A_RENOUVELER");
        assertThat(etatCopie(enAttente)).isEqualTo("BLOQUEE");
    }

    @Test
    void ca67_unDroitDriveDecochePasseARenouveler() {
        Object bloque = valider().json().get("id");
        GOOGLE.repondreJeton(3600, GoogleStub.SHEETS, GoogleStub.JETON_RENOUVELLEMENT);

        Reponse fin = autoriser();

        assertThat(fin.enTete("Location")).isEqualTo("/");
        assertThat(etat()).isEqualTo("A_RENOUVELER");
        assertThat(etatCopie(bloque)).isEqualTo("BLOQUEE");
        assertThat(ligneAutorisation()).containsEntry("jeton_renouvellement_chiffre", null).containsEntry("jeton_acces_chiffre", null);
        assertThat(jetons.accessToken()).isEmpty();
    }

    /** Sans jeton de renouvellement, l'autorisation ne survivrait pas à l'expiration du jeton d'accès : elle n'est pas donnée « une fois ». */
    @Test
    void ca67_unConsentementSansJetonDeRenouvellementPasseARenouveler() {
        GOOGLE.repondreJeton(3600, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, null);

        autoriser();

        assertThat(etat()).isEqualTo("A_RENOUVELER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"?code=code-pirate", "?code=code-pirate&state=faux", "?code=code-pirate&state="})
    void cs22_unStateAbsentOuFauxNeDemandeAucunJetonEtLaisseLAutorisationIntacte(String requete) {
        autoriser();
        Map<String, Object> avant = ligneAutorisation();
        GOOGLE.reinitialiser();

        Reponse fin = client.get("/api/google/rappel" + requete);

        assertThat(fin.statut()).isEqualTo(302);
        assertThat(fin.enTete("Location")).isEqualTo("/");
        assertThat(GOOGLE.requetes()).isEmpty();
        assertThat(ligneAutorisation()).isEqualTo(avant);
    }

    @Test
    void cs22_unStateDUneAutreSessionNeDemandeAucunJetonEtLaDemandeEnCoursResteValable() {
        String stateDeLaVictime = parametres(client.get("/api/google/autorisation").enTete("Location")).get("state");
        TestClient autre = connecte();
        autre.get("/api/google/autorisation");

        Reponse fin = autre.get("/api/google/rappel?code=code-pirate&state=" + stateDeLaVictime);

        assertThat(fin.enTete("Location")).isEqualTo("/");
        assertThat(GOOGLE.requetes()).isEmpty();
        assertThat(etat()).isEqualTo("ABSENTE");
    }

    @Test
    void cs22_unStateDejaUtiliseNeDemandeAucunNouveauJeton() {
        String state = parametres(client.get("/api/google/autorisation").enTete("Location")).get("state");
        rappel("/api/google/rappel?code=code-temoin&state=" + state);
        Map<String, Object> avant = ligneAutorisation();
        GOOGLE.reinitialiser();

        Reponse fin = client.get("/api/google/rappel?code=code-temoin&state=" + state);

        assertThat(fin.enTete("Location")).isEqualTo("/");
        assertThat(GOOGLE.requetes()).isEmpty();
        assertThat(ligneAutorisation()).isEqualTo(avant);
    }

    @Test
    void cs23_lesJetonsNApparaissentEnClairNiDansLeStockageNiDansLAPI(CapturedOutput journal) throws IOException {
        autoriser();

        assertThat(ligneAutorisation().values().toString())
                .doesNotContain(GoogleStub.JETON_RENOUVELLEMENT).doesNotContain(GoogleStub.JETON_ACCES);
        String fichier = base.execute((ConnectionCallback<String>) lien -> lien.getMetaData().getURL()).replaceFirst("jdbc:sqlite:", "").replaceFirst("\\?.*", "");
        String octets = new String(Files.readAllBytes(Path.of(fichier)), StandardCharsets.ISO_8859_1);
        assertThat(octets).doesNotContain(GoogleStub.JETON_RENOUVELLEMENT).doesNotContain(GoogleStub.JETON_ACCES);
        assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"VALIDE\"}");
        assertThat(journal.getOut()).doesNotContain(GoogleStub.JETON_RENOUVELLEMENT).doesNotContain(GoogleStub.JETON_ACCES);
        assertThat(jetons.accessToken()).contains(GoogleStub.JETON_ACCES);
    }

    @Test
    void cs24_leParametreRedirectDuRappelEstIgnore() {
        String state = parametres(client.get("/api/google/autorisation").enTete("Location")).get("state");
        String piege = GOOGLE.adresse() + "/piege";

        Reponse premiere = client.get("/api/google/rappel?code=code-temoin&state=" + state + "&redirect=https://exemple.invalid&redirect=" + piege);

        assertThat(premiere.statut()).isEqualTo(302);
        assertThat(premiere.enTete("Location")).isEqualTo("http://localhost:" + port + "/api/google/rappel");
        assertThat(etat()).isEqualTo("VALIDE");
        assertThat(client.get("/api/google/rappel?redirect=https://exemple.invalid").enTete("Location")).isEqualTo("/");
        assertThat(client.get("/api/google/rappel?code=c&state=faux&redirect=https://exemple.invalid").enTete("Location")).isEqualTo("/");
        assertThat(GOOGLE.requetes()).extracting(GoogleStub.Requete::chemin).containsExactly("/jeton");
    }
}
