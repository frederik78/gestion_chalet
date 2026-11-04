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
import java.util.ArrayList;
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
import org.junit.jupiter.params.provider.CsvSource;
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
        registre.add("recus.google.adresse-consentement", GOOGLE::adresseConsentement);
        registre.add("recus.google.adresse-jeton", GOOGLE::adresseJeton);
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

        rappel("/api/google/rappel?code=code-temoin&state=" + stateDeLaVictime);

        assertThat(GOOGLE.requetes()).hasSize(1);
        assertThat(etat()).isEqualTo("VALIDE");
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

    /** Toutes les adresses de redirection d'un rappel, du filtre de Spring Security à la sortie vers la PWA. */
    private List<String> chaineDeRedirections(String chemin) {
        List<String> adresses = new ArrayList<>();
        String origine = "http://localhost:" + port;
        Reponse reponse = client.get(chemin);
        while (reponse.statut() == 302) {
            String adresse = reponse.enTete("Location");
            adresses.add(adresse);
            if (!adresse.startsWith(origine + "/api/google/rappel")) {
                break;
            }
            reponse = client.get(adresse.substring(origine.length()));
        }
        return adresses;
    }

    private String nouveauState() {
        return parametres(client.get("/api/google/autorisation").enTete("Location")).get("state");
    }

    private void assertSansTexteTechnique(List<String> adresses) {
        assertThat(adresses).isNotEmpty().last().isEqualTo("/");
        assertThat(adresses).allSatisfy(adresse -> assertThat(URLDecoder.decode(adresse, StandardCharsets.UTF_8))
                .doesNotContain("error_description").doesNotContain("error_uri")
                .doesNotContain("Unauthorized").doesNotContain("OAuth2").doesNotContain("Exception")
                .doesNotContain(GOOGLE.adresseJeton()).doesNotContain(GoogleStub.JETON_ACCES));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "401 | ''",
        "401 | {\"error\":\"invalid_client\",\"error_description\":\"Unauthorized\",\"error_uri\":\"https://exemple.invalid\"}",
        "200 | ceci n'est pas du JSON",
        "200 | {\"access_token\":\"jeton-acces-temoin\"}"
    })
    void cs24_unEchangeEnEchecNeLaisseAucunTexteTechniqueDansLesRedirections(int statut, String corps) {
        String state = nouveauState();
        GOOGLE.repondre(statut, corps);

        assertSansTexteTechnique(chaineDeRedirections("/api/google/rappel?code=code-temoin&state=" + state));
    }

    @Test
    void cs24_unRefusNeRecopiePasLaDescriptionRecueDansLesRedirections() {
        String state = nouveauState();

        assertSansTexteTechnique(chaineDeRedirections("/api/google/rappel?error=access_denied"
                + "&error_description=Exception-recopiee&error_uri=https://exemple.invalid&state=" + state));
    }

    @Test
    void ca65_unRefusDeConsentementEstJournaliseParSonCodeSansSaDescription(CapturedOutput journal) {
        String state = nouveauState();

        chaineDeRedirections("/api/google/rappel?error=access_denied&error_description=description-temoin&state=" + state);

        assertThat(journal.getOut()).contains("Rappel Google en échec : access_denied")
                .doesNotContain("description-temoin").doesNotContain("STATE_INVALIDE");
    }

    @Test
    void ca65_unEchangeEnEchecEstJournaliseParSonCode(CapturedOutput journal) {
        String state = nouveauState();
        GOOGLE.repondre(401, "");

        chaineDeRedirections("/api/google/rappel?code=code-temoin&state=" + state);

        assertThat(journal.getOut()).contains("Rappel Google en échec : invalid_token_response")
                .doesNotContain(GOOGLE.adresseJeton()).doesNotContain("STATE_INVALIDE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"code-inconnu-temoin", "access_denied%0d%0aFAUX-JOURNAL-temoin", "ACCESS_DENIED-temoin", ""})
    void ca65_unCodeInconnuEstJournaliseAutreSansLaValeurRecue(String code, CapturedOutput journal) {
        Reponse fin = client.get("/api/google/rappel?error=" + code);

        assertThat(fin.enTete("Location")).isEqualTo("/");
        assertThat(journal.getOut()).contains("Rappel Google en échec : AUTRE").doesNotContain("temoin");
    }

    @Test
    void ca65_deuxCodesRecusSontJournalisesAutre(CapturedOutput journal) {
        client.get("/api/google/rappel?error=access_denied&error=server_error");

        assertThat(journal.getOut()).contains("Rappel Google en échec : AUTRE").doesNotContain("access_denied");
    }

    @Test
    void cs22_unStateFauxResteJournaliseStateInvalide(CapturedOutput journal) {
        client.get("/api/google/rappel?code=c&state=faux");

        assertThat(journal.getOut()).contains("Rappel Google refusé : STATE_INVALIDE").doesNotContain("Rappel Google en échec");
    }

    private void assertAutorisationIntacte(Map<String, Object> avant, List<String> redirections, CapturedOutput journal) {
        assertThat(redirections).last().isEqualTo("/");
        assertThat(ligneAutorisation()).isEqualTo(avant);
        assertThat(etat()).isEqualTo("VALIDE");
        assertThat(journal.getOut()).doesNotContain(GoogleStub.JETON_ACCES).doesNotContain(GoogleStub.JETON_RENOUVELLEMENT);
    }

    @Test
    void cs22_unRefusAvecUnStateValideLaisseLAutorisationIntacte(CapturedOutput journal) {
        autoriser();
        Map<String, Object> avant = ligneAutorisation();
        String state = nouveauState();
        GOOGLE.reinitialiser();

        List<String> redirections = chaineDeRedirections("/api/google/rappel?error=access_denied&state=" + state);

        assertThat(GOOGLE.requetes()).isEmpty();
        assertAutorisationIntacte(avant, redirections, journal);
    }

    @Test
    void cs22_unEchangeEnEchecLaisseLAutorisationIntacte(CapturedOutput journal) {
        autoriser();
        Map<String, Object> avant = ligneAutorisation();
        String state = nouveauState();
        GOOGLE.repondre(401, "{\"access_token\":\"" + GoogleStub.JETON_ACCES + "\",\"refresh_token\":\"" + GoogleStub.JETON_RENOUVELLEMENT + "\"}");

        List<String> redirections = chaineDeRedirections("/api/google/rappel?code=code-temoin&state=" + state);

        assertAutorisationIntacte(avant, redirections, journal);
    }

    /** Google peut taire les droits accordés : sans preuve qu'ils le sont tous, l'autorisation n'est pas donnée. */
    @Test
    void ca67_uneReponseSansDroitsPasseARenouveler() {
        GOOGLE.repondreJeton(3600, null, GoogleStub.JETON_RENOUVELLEMENT);

        autoriser();

        assertThat(etat()).isEqualTo("A_RENOUVELER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"jeton_acces_chiffre", "jeton_renouvellement_chiffre"})
    void ca66_unChiffreTropCourtPasseARenouveler(String colonne) {
        autoriser();
        Object enAttente = valider().json().get("id");
        base.update("update autorisation_google set " + colonne + " = 'AAAA'");

        assertThat(jetons.accessToken()).isEmpty();

        assertThat(etat()).isEqualTo("A_RENOUVELER");
        assertThat(etatCopie(enAttente)).isEqualTo("BLOQUEE");
    }

    private Object autoriserPuisValiderUnRecu() {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        return valider().json().get("id");
    }

    /** D-57 : le jeton reste, chiffré, pour reprendre sans nouveau consentement. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "400 | {\"error\":\"invalid_client\",\"error_description\":\"description-temoin\"} | invalid_client",
        "401 | {\"error\":\"invalid_client\",\"error_description\":\"description-temoin\"} | invalid_client",
        "400 | {\"error\":\"code-recu-temoin\"}                                                 | AUTRE",
        "403 | {\"error\":\"code-recu-temoin\"}                                                 | AUTRE"
    })
    void ca66_unRefusPasseARenouvelerSansEffacerLeJeton(int statut, String corps, String code, CapturedOutput journal) {
        Object enAttente = autoriserPuisValiderUnRecu();
        Map<String, Object> avant = ligneAutorisation();
        GOOGLE.repondre(statut, corps);

        assertThat(jetons.accessToken()).isEmpty();

        assertThat(client.get("/api/google/etat").corps()).isEqualTo("{\"etat\":\"A_RENOUVELER\"}");
        assertThat(etatCopie(enAttente)).isEqualTo("BLOQUEE");
        assertThat(ligneAutorisation().get("jeton_renouvellement_chiffre")).isEqualTo(avant.get("jeton_renouvellement_chiffre")).isNotNull();
        assertThat(ligneAutorisation().values().toString()).doesNotContain(GoogleStub.JETON_RENOUVELLEMENT);
        assertThat(journal.getOut()).contains("ERROR").contains("Renouvellement Google refusé : " + code)
                .doesNotContain("temoin");
    }

    @Test
    void ca66_apresUnRefusUnRenouvellementReussiRepasseAValideSansConsentementEtUnSeulAppelParEssai() {
        Object enAttente = autoriserPuisValiderUnRecu();
        GOOGLE.repondre(401, "{\"error\":\"invalid_client\"}");
        assertThat(jetons.accessToken()).isEmpty();
        assertThat(renouvellements()).hasSize(1);

        assertThat(jetons.accessToken()).isEmpty();
        assertThat(renouvellements()).hasSize(2);
        assertThat(etat()).isEqualTo("A_RENOUVELER");

        GOOGLE.repondreJeton(3600, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, null);
        assertThat(jetons.accessToken()).contains(GoogleStub.JETON_ACCES);

        assertThat(renouvellements()).hasSize(3).allSatisfy(requete ->
                assertThat(requete.parametres()).containsEntry("refresh_token", GoogleStub.JETON_RENOUVELLEMENT));
        assertThat(etat()).isEqualTo("VALIDE");
        assertThat(etatCopie(enAttente)).isEqualTo("A_COPIER");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"400 | invalid_grant", "401 | invalid_grant", "400 | invalid_token"})
    void ca66_uneRevocationEffaceLeJeton(int statut, String code) {
        Object enAttente = autoriserPuisValiderUnRecu();
        GOOGLE.repondre(statut, "{\"error\":\"" + code + "\"}");

        assertThat(jetons.accessToken()).isEmpty();

        assertThat(etat()).isEqualTo("A_RENOUVELER");
        assertThat(etatCopie(enAttente)).isEqualTo("BLOQUEE");
        assertThat(ligneAutorisation()).containsEntry("jeton_renouvellement_chiffre", null).containsEntry("jeton_acces_chiffre", null);
    }

    @Test
    void ca66_apresUnRefusUneRevocationEffaceLeJeton() {
        autoriserPuisValiderUnRecu();
        GOOGLE.repondre(401, "{\"error\":\"invalid_client\"}");
        jetons.accessToken();
        GOOGLE.repondre(400, "{\"error\":\"invalid_grant\"}");

        assertThat(jetons.accessToken()).isEmpty();

        assertThat(ligneAutorisation()).containsEntry("jeton_renouvellement_chiffre", null);
        GOOGLE.reinitialiser();
        assertThat(jetons.accessToken()).isEmpty();
        assertThat(GOOGLE.requetes()).isEmpty();
    }

    /** D-57 : une réponse illisible ou un de ces deux codes, quel que soit le statut, ne prouve aucun refus. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "200 | ceci n'est pas du JSON",
        "200 | <html>proxy</html>",
        "200 | ''",
        "401 | ''",
        "403 | <html>proxy</html>",
        "400 | <html>proxy</html>",
        "400 | {}",
        "400 | {\"error\":\"temporarily_unavailable\"}",
        "400 | {\"error\":\"server_error\"}",
        "401 | {\"error\":\"temporarily_unavailable\"}",
        "403 | {\"error\":\"server_error\"}"
    })
    void ca66_uneReponseIllisibleOuUnCodePassagerGardeLAutorisation(int statut, String corps) {
        Object enAttente = autoriserPuisValiderUnRecu();
        Map<String, Object> avant = ligneAutorisation();
        GOOGLE.repondre(statut, corps);

        assertThatThrownBy(() -> jetons.accessToken()).isInstanceOf(OAuth2AuthorizationException.class);

        assertThat(etat()).isEqualTo("VALIDE");
        assertThat(etatCopie(enAttente)).isEqualTo("A_COPIER");
        assertThat(ligneAutorisation()).isEqualTo(avant);
    }

    @Test
    void ca66_uneReponseEnTypeHtmlGardeLAutorisation() {
        autoriserPuisValiderUnRecu();
        GOOGLE.repondreHtml(200, "<html>proxy</html>");

        assertThatThrownBy(() -> jetons.accessToken()).isInstanceOf(OAuth2AuthorizationException.class);

        assertThat(etat()).isEqualTo("VALIDE");
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void ca66_unePannePassagereGardeLAutorisation(int statut) {
        GOOGLE.repondreJeton(1, GoogleStub.SHEETS + " " + GoogleStub.DRIVE_FILE, GoogleStub.JETON_RENOUVELLEMENT);
        autoriser();
        GOOGLE.repondre(statut, "{\"error\":\"invalid_client\"}");

        assertThatThrownBy(() -> jetons.accessToken()).isInstanceOf(OAuth2AuthorizationException.class);

        assertThat(etat()).isEqualTo("VALIDE");
    }
}
