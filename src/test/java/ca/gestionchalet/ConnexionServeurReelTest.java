package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.HorlogeEssai;
import ca.gestionchalet.support.ServeurReelTest;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

@ServeurReelTest
class ConnexionServeurReelTest {

    private static final int ECHECS_PAR_DEFAUT = 5;

    @LocalServerPort
    int port;

    @Autowired
    HorlogeEssai horloge;

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
        assertThat(reponse.setCookie("XSRF-TOKEN").getLast()).contains("SameSite=Lax", "Secure").doesNotContain("HttpOnly");
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

    private ClientEssai clientBloque() {
        ClientEssai client = client();
        for (int essai = 0; essai < ECHECS_PAR_DEFAUT; essai++) {
            assertThat(client.connecter("faux").statut()).isEqualTo(401);
        }
        return client;
    }

    @Test
    void cs05_apresCinqEchecsLeBonMotDePasseRecoit429AvecRetryAfter() {
        ClientEssai client = clientBloque();

        Reponse reponse = client.connecter(ConfigurationEssai.MOT_DE_PASSE);

        assertThat(reponse.statut()).isEqualTo(429);
        assertThat(reponse.enTete("Retry-After")).isEqualTo("900");
        assertThat(reponse.json()).isEqualTo(Map.of("erreur", "Trop de tentatives. Réessayez plus tard.", "code", "TROP_DE_TENTATIVES"));
        assertThat(client.cookie("JSESSIONID")).isNull();
    }

    @Test
    void cs05_apresLeDelaiLeBonMotDePasseReussit() {
        ClientEssai client = clientBloque();
        horloge.avancer(Duration.ofMinutes(15).minusSeconds(1));
        Reponse encoreBloque = client.connecter(ConfigurationEssai.MOT_DE_PASSE);
        assertThat(encoreBloque.statut()).isEqualTo(429);
        assertThat(encoreBloque.enTete("Retry-After")).isEqualTo("1");

        horloge.avancer(Duration.ofSeconds(1));

        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    @Test
    void cs05_uneAutreSourceNEstPasBloquee() {
        clientBloque();

        assertThat(client().connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    @Test
    void cs05_lEnTeteXForwardedForNeContournePasLeBlocage() {
        ClientEssai client = clientBloque();
        client.get("/actuator/health");

        Reponse reponse = client.envoyer(client.requete("/api/connexion")
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                .header("X-Forwarded-For", "203.0.113.9")
                .POST(HttpRequest.BodyPublishers.ofString("{\"motDePasse\":\"" + ConfigurationEssai.MOT_DE_PASSE + "\"}"))
                .build());

        assertThat(reponse.statut()).isEqualTo(429);
    }

    @Test
    void cs06_apresDeconnexionLAncienCookieDonne401() {
        ClientEssai client = client();
        client.connecter(ConfigurationEssai.MOT_DE_PASSE);
        ClientEssai ancien = client();
        ancien.copierCookiesDe(client);
        assertThat(ancien.get("/api/essai/session").statut()).isEqualTo(200);

        Reponse deconnexion = client.deconnecter();

        assertThat(deconnexion.statut()).isEqualTo(204);
        assertThat(deconnexion.setCookie("JSESSIONID").getFirst()).contains("Max-Age=0", "HttpOnly", "SameSite=Lax", "Secure");
        assertThat(client.cookie("JSESSIONID")).isNull();
        Reponse rejeu = ancien.get("/api/essai/session");
        assertThat(rejeu.statut()).isEqualTo(401);
        assertThat(rejeu.json()).isEqualTo(Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE"));
    }

    /** bcrypt dure plusieurs dizaines de millisecondes : c'est la fenêtre dans laquelle des requêtes simultanées échapperaient au compte. */
    @Test
    void cs05_desRequetesSimultaneesNeDepassentPasLaLimite() throws Exception {
        int simultanees = 50;
        ClientEssai client = client();
        client.get("/actuator/health");
        List<HttpRequest> requetes = new ArrayList<>();
        for (int i = 0; i < simultanees; i++) {
            requetes.add(client.requete("/api/connexion")
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", client.cookie("XSRF-TOKEN"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"motDePasse\":\"faux\"}"))
                    .build());
        }

        CountDownLatch depart = new CountDownLatch(1);
        List<Future<Integer>> statuts = new ArrayList<>();
        try (ExecutorService fils = Executors.newFixedThreadPool(simultanees)) {
            for (HttpRequest requete : requetes) {
                statuts.add(fils.submit(() -> {
                    depart.await();
                    return client.envoyer(requete).statut();
                }));
            }
            depart.countDown();
            Map<Integer, Integer> parStatut = new TreeMap<>();
            for (Future<Integer> statut : statuts) {
                parStatut.merge(statut.get(), 1, Integer::sum);
            }

            assertThat(parStatut).isEqualTo(Map.of(401, ECHECS_PAR_DEFAUT, 429, simultanees - ECHECS_PAR_DEFAUT));
        }
    }

    @Test
    void cs05_uneConnexionReussieRemetLeCompteAZero() {
        ClientEssai client = client();
        for (int essai = 0; essai < ECHECS_PAR_DEFAUT - 1; essai++) {
            assertThat(client.connecter("faux").statut()).isEqualTo(401);
        }
        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);

        for (int essai = 0; essai < ECHECS_PAR_DEFAUT; essai++) {
            assertThat(client.connecter("faux").statut()).as("échec n° %d après la réussite", essai + 1).isEqualTo(401);
        }
        assertThat(client.connecter("faux").statut()).isEqualTo(429);
    }
}
