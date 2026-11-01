package ca.gestionchalet.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.json.JsonMapper;

/** Client HTTP réel pour les tests : garde ses cookies comme un navigateur, et se présente sous une source donnée. */
public final class ClientEssai {

    public static final String EN_TETE_SOURCE = "X-Source-Essai";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String base;
    private final String source;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String, String> cookies = new LinkedHashMap<>();

    public ClientEssai(int port, String source) {
        this.base = "http://localhost:" + port;
        this.source = source;
    }

    public record Reponse(int statut, String corps, Map<String, List<String>> enTetes) {

        public Map<String, Object> json() {
            @SuppressWarnings("unchecked")
            Map<String, Object> valeurs = JSON.readValue(corps, Map.class);
            return valeurs;
        }

        public String enTete(String nom) {
            return enTetes.getOrDefault(nom.toLowerCase(), List.of()).stream().findFirst().orElse(null);
        }

        public List<String> setCookie(String nom) {
            return enTetes.getOrDefault("set-cookie", List.of()).stream()
                    .filter(valeur -> valeur.startsWith(nom + "="))
                    .toList();
        }
    }

    public String cookie(String nom) {
        return cookies.get(nom);
    }

    public void poserCookie(String nom, String valeur) {
        cookies.put(nom, valeur);
    }

    /** Copie la session et le jeton d'un autre client, pour rejouer un ancien cookie. */
    public void copierCookiesDe(ClientEssai autre) {
        cookies.clear();
        cookies.putAll(autre.cookies);
    }

    public HttpRequest.Builder requete(String chemin) {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(base + chemin)).header(EN_TETE_SOURCE, source);
        if (!cookies.isEmpty()) {
            requete.header("Cookie", cookies.entrySet().stream()
                    .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
        return requete;
    }

    public Reponse envoyer(HttpRequest requete) {
        try {
            HttpResponse<String> reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
            Map<String, List<String>> enTetes = reponse.headers().map();
            enTetes.getOrDefault("set-cookie", List.of()).forEach(this::retenirCookie);
            return new Reponse(reponse.statusCode(), reponse.body(), enTetes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void retenirCookie(String setCookie) {
        String[] nomValeur = setCookie.split(";", 2)[0].split("=", 2);
        if (setCookie.contains("Max-Age=0") || nomValeur[1].isEmpty()) {
            cookies.remove(nomValeur[0]);
        } else {
            cookies.put(nomValeur[0], nomValeur[1]);
        }
    }

    public Reponse get(String chemin) {
        return envoyer(requete(chemin).GET().build());
    }

    /** Envoie un corps JSON avec le jeton CSRF courant, obtenu au besoin comme le ferait la page de connexion. */
    public Reponse appelAvecJeton(String methode, String chemin, String corpsJson) {
        if (cookie("XSRF-TOKEN") == null) {
            get("/actuator/health");
        }
        return appelSansJeton(methode, chemin, corpsJson, cookie("XSRF-TOKEN"));
    }

    /** Envoie un corps JSON avec cet en-tête CSRF ({@code null} : pas d'en-tête). */
    public Reponse appelSansJeton(String methode, String chemin, String corpsJson, String enTeteCsrf) {
        HttpRequest.Builder requete = requete(chemin)
                .header("Content-Type", "application/json")
                .method(methode, HttpRequest.BodyPublishers.ofString(corpsJson));
        if (enTeteCsrf != null) {
            requete.header("X-XSRF-TOKEN", enTeteCsrf);
        }
        return envoyer(requete.build());
    }

    public Reponse connecter(String motDePasse) {
        return appelAvecJeton("POST", "/api/connexion", "{\"motDePasse\":\"" + motDePasse + "\"}");
    }

    public Reponse deconnecter() {
        return appelAvecJeton("POST", "/api/deconnexion", "");
    }
}
