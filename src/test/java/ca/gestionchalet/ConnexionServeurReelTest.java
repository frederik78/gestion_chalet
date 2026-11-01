package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.ServeurReelTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

@ServeurReelTest
class ConnexionServeurReelTest {

    @LocalServerPort
    int port;

    private ClientEssai client() {
        return new ClientEssai(port, "source-" + UUID.randomUUID());
    }

    @Test
    void cs04_mauvaisMotDePasseDonne401SansSession() {
        ClientEssai client = client();

        Reponse reponse = client.connecter("faux");

        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Identifiants invalides.", "code", "IDENTIFIANTS_INVALIDES"));
        assertThat(client.cookie("JSESSIONID")).isNull();
    }

    @Test
    void cs04_bonMotDePasseOuvreUneSessionAuCookieProtege() {
        ClientEssai client = client();

        Reponse reponse = client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(reponse.statut()).isEqualTo(204);
        List<String> cookieSession = reponse.setCookie("JSESSIONID");
        assertThat(cookieSession).hasSize(1);
        assertThat(cookieSession.getFirst()).contains("HttpOnly", "SameSite=Lax", "Secure");
    }

    @Test
    void cs04_lIdentifiantDeSessionChangeAChaqueConnexion() {
        ClientEssai client = client();
        client.connecter(ConfigurationEssai.MOT_DE_PASSE);
        String premiere = client.cookie("JSESSIONID");

        client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(premiere).isNotNull();
        assertThat(client.cookie("JSESSIONID")).isNotNull().isNotEqualTo(premiere);
    }

    @Test
    void cs04_leJetonCsrfChangeALaConnexion() {
        ClientEssai client = client();
        client.get("/actuator/health");
        String avant = client.cookie("XSRF-TOKEN");

        client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(avant).isNotNull();
        assertThat(client.cookie("XSRF-TOKEN")).isNotNull().isNotEqualTo(avant);
    }

    @Test
    void cs04_motDePasseAbsentDonne400Validation() {
        Reponse reponse = client().appelAvecJeton("POST", "/api/connexion", "{}");

        assertThat(reponse.statut()).isEqualTo(400);
        assertThat(reponse.json()).isEqualTo(Map.of(
                "erreur", "Certains champs sont invalides.",
                "code", "VALIDATION",
                "champs", List.of(Map.of("champ", "motDePasse", "code", "OBLIGATOIRE"))));
    }

    @Test
    void cs04_motDePasseBorneA1000Caracteres() {
        assertThat(client().connecter("x".repeat(1000)).statut()).isEqualTo(401);

        Reponse trop = client().connecter("x".repeat(1001));

        assertThat(trop.statut()).isEqualTo(400);
        assertThat(trop.json()).isEqualTo(Map.of(
                "erreur", "Certains champs sont invalides.",
                "code", "VALIDATION",
                "champs", List.of(Map.of("champ", "motDePasse", "code", "BORNE"))));
    }
}
