package ca.gestionchalet;

import static org.assertj.core.api.Assertions.assertThat;

import ca.gestionchalet.support.ClientEssai;
import ca.gestionchalet.support.ClientEssai.Reponse;
import ca.gestionchalet.support.ConfigurationEssai;
import ca.gestionchalet.support.RouteEssaiResource;
import ca.gestionchalet.support.ServeurReelTest;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;

/** Parcourt les routes réellement enregistrées : une route ajoutée par une issue suivante est couverte d'office. */
@ServeurReelTest
class AccesEtCsrfServeurReelTest {

    private static final Map<String, String> NON_AUTHENTIFIE = Map.of("erreur", "Authentification requise.", "code", "NON_AUTHENTIFIE");
    private static final Map<String, String> CSRF_INVALIDE = Map.of("erreur", "Jeton de sécurité absent ou invalide.", "code", "CSRF_INVALIDE");

    private static final List<String> METHODES = List.of("GET", "POST", "PUT", "DELETE", "PATCH");
    private static final Set<String> PUBLIQUES = Set.of("GET /actuator/health", "POST /api/connexion");
    private static final List<String> CHEMINS_INCONNUS = List.of(
            "/api/inconnue", "/inconnue", "/actuator", "/actuator/env", "/actuator/beans", "/actuator/health/liveness",
            "/actuator/heapdump", "/error", "/index.php", "/h2-console");

    @LocalServerPort
    int port;

    @Autowired
    List<RequestMappingInfoHandlerMapping> tablesDeRoutes;

    @Autowired
    RouteEssaiResource routeEssai;

    private record Route(String methode, String chemin) {
    }

    private ClientEssai client() {
        return new ClientEssai(port, "source-" + UUID.randomUUID());
    }

    private ClientEssai connecte() {
        ClientEssai client = client();
        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);
        return client;
    }

    private static String concret(String motif) {
        return motif.replaceAll("\\{[^}]*}", UUID.randomUUID().toString());
    }

    private Set<Route> routesEnregistrees() {
        return tablesDeRoutes.stream()
                .flatMap(table -> table.getHandlerMethods().keySet().stream())
                .flatMap(info -> routes(info).stream())
                .collect(Collectors.toSet());
    }

    private static List<Route> routes(RequestMappingInfo info) {
        Collection<RequestMethod> declarees = info.getMethodsCondition().getMethods();
        List<String> methodes = declarees.isEmpty() ? METHODES : declarees.stream().map(Enum::name).toList();
        return info.getPatternValues().stream()
                .flatMap(motif -> methodes.stream().map(methode -> new Route(methode, motif)))
                .toList();
    }

    private Set<Route> routesAVerifierSansSession() {
        Set<Route> routes = routesEnregistrees().stream()
                .filter(route -> !PUBLIQUES.contains(route.methode() + " " + route.chemin()))
                .collect(Collectors.toSet());
        for (String chemin : CHEMINS_INCONNUS) {
            METHODES.forEach(methode -> routes.add(new Route(methode, chemin)));
        }
        return routes;
    }

    @Test
    void lesRoutesAttenduesSontBienParcourues() {
        assertThat(routesEnregistrees()).contains(
                new Route("POST", "/api/connexion"), new Route("POST", "/api/deconnexion"),
                new Route("POST", "/api/essai/mutation/{id}"), new Route("PUT", "/api/essai/mutation/{id}"),
                new Route("GET", "/actuator/health"));
    }

    @Test
    void cs01_sansSessionToutRepond401SansDonnee() {
        Set<String> ecarts = new TreeSet<>();
        for (Route route : routesAVerifierSansSession()) {
            String chemin = concret(route.chemin());
            String nom = route.methode() + " " + chemin;
            Reponse sansJeton = client().appelSansJeton(route.methode(), chemin, "{}", null);
            Reponse avecJeton = client().appelAvecJeton(route.methode(), chemin, "{}");
            for (Reponse reponse : List.of(sansJeton, avecJeton)) {
                if (reponse.statut() != 401 || !reponse.json().equals(NON_AUTHENTIFIE) || !reponse.setCookie("JSESSIONID").isEmpty()) {
                    ecarts.add(nom + " -> " + reponse.statut() + " " + reponse.corps() + " " + reponse.setCookie("JSESSIONID"));
                }
            }
        }
        assertThat(ecarts).isEmpty();
    }

    /** Le corps dépasse la borne de 64 Ko : sans session, il n'est même pas lu, et le refus reste celui de la sécurité. */
    @Test
    void cs01_unGrosCorpsSansSessionDonne401() {
        Reponse reponse = client().appelAvecJeton("POST", "/api/essai/mutation/1", "x".repeat(70_000));

        assertThat(reponse.statut()).isEqualTo(401);
        assertThat(reponse.json()).isEqualTo(NON_AUTHENTIFIE);
    }

    @Test
    void cs01_santePubliqueSansDetail() {
        Reponse reponse = client().get("/actuator/health");

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(reponse.json()).isEqualTo(Map.of("status", "UP"));
    }

    @Test
    void cs01_authentificationBasicRefusee() {
        String basic = Base64.getEncoder().encodeToString(("user:" + ConfigurationEssai.MOT_DE_PASSE).getBytes(StandardCharsets.UTF_8));
        ClientEssai client = client();
        HttpRequest requete = client.requete("/api/essai/session").header("Authorization", "Basic " + basic).GET().build();

        assertThat(client.envoyer(requete).statut()).isEqualTo(401);
    }

    @Test
    void cs01_avecSessionUneRouteInconnueDonne404Generique() {
        ClientEssai client = connecte();

        for (String chemin : List.of("/api/inconnue", "/inconnue", "/actuator/env", "/actuator")) {
            Reponse reponse = client.get(chemin);
            assertThat(reponse.statut()).as(chemin).isEqualTo(404);
            assertThat(reponse.json()).as(chemin).isEqualTo(Map.of("erreur", "Ressource introuvable.", "code", "INTROUVABLE"));
        }
    }

    private Set<Route> routesEnEcriture() {
        return routesEnregistrees().stream()
                .filter(route -> route.methode().equals("POST") || route.methode().equals("PUT"))
                .collect(Collectors.toSet());
    }

    @Test
    void cs02_avecSessionSansJetonOuJetonFauxToutEstRefuseEtRienNeChange() {
        ClientEssai client = connecte();
        int mutationsAvant = routeEssai.mutations.get();
        String sessionAvant = client.cookie("JSESSIONID");

        for (Route route : routesEnEcriture()) {
            String chemin = concret(route.chemin());
            for (String jeton : new String[] {null, "jeton-faux"}) {
                Reponse reponse = client.appelSansJeton(route.methode(), chemin, "{\"motDePasse\":\"" + ConfigurationEssai.MOT_DE_PASSE + "\"}", jeton);
                assertThat(reponse.statut()).as(route + " jeton=" + jeton).isEqualTo(403);
                assertThat(reponse.json()).as(route.toString()).isEqualTo(CSRF_INVALIDE);
            }
        }

        assertThat(routeEssai.mutations.get()).isEqualTo(mutationsAvant);
        assertThat(client.cookie("JSESSIONID")).isEqualTo(sessionAvant);
        assertThat(client.get("/api/essai/session").statut()).isEqualTo(200);
    }

    @Test
    void cs02_leJetonValideLaisseLaMutationPasser() {
        ClientEssai client = connecte();
        int mutationsAvant = routeEssai.mutations.get();

        Reponse reponse = client.appelAvecJeton("PUT", "/api/essai/mutation/1", "{}");

        assertThat(reponse.statut()).isEqualTo(200);
        assertThat(routeEssai.mutations.get()).isEqualTo(mutationsAvant + 1);
    }

    @Test
    void cs02_connexionSansJetonNOuvreAucuneSessionEtNeCompteAucunEchec() {
        ClientEssai client = client();

        for (int essai = 0; essai < 7; essai++) {
            Reponse reponse = client.appelSansJeton("POST", "/api/connexion", "{\"motDePasse\":\"faux\"}", null);
            assertThat(reponse.statut()).isEqualTo(403);
            assertThat(reponse.json()).isEqualTo(CSRF_INVALIDE);
        }
        Reponse bonSansJeton = client.appelSansJeton("POST", "/api/connexion", "{\"motDePasse\":\"" + ConfigurationEssai.MOT_DE_PASSE + "\"}", "jeton-faux");

        assertThat(bonSansJeton.statut()).isEqualTo(403);
        assertThat(client.cookie("JSESSIONID")).isNull();
        assertThat(client.connecter(ConfigurationEssai.MOT_DE_PASSE).statut()).isEqualTo(204);
    }

    @Test
    void cs02_leJetonEnParametreNEstPasAccepte() {
        ClientEssai client = connecte();

        Reponse reponse = client.appelSansJeton("POST", "/api/essai/mutation/1?_csrf=" + client.cookie("XSRF-TOKEN"), "{}", null);

        assertThat(reponse.statut()).isEqualTo(403);
    }
}
