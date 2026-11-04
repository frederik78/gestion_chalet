package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.GoogleStub;
import ca.gestionchalet.support.RealServerTest;
import ca.gestionchalet.support.RecuRequests;
import ca.gestionchalet.support.TestClient;
import ca.gestionchalet.support.TestClient.Reponse;
import ca.gestionchalet.support.TestDoublesConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;

/** Autorisation Google de bout en bout : serveur réel, session, base temporaire, et un serveur local à la place de Google. */
@RealServerTest
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
    }

    @AfterAll
    static void arreterGoogle() {
        GOOGLE.close();
    }

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate base;

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
}
